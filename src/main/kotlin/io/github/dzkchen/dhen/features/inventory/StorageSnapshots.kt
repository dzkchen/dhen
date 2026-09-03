package io.github.dzkchen.dhen.features.inventory

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.data.ProfileHooks
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

internal object StorageSnapshots {
	const val PAGES = 27
	const val ENDER_PAGES = 9
	const val ROW_WIDTH = 9
	const val HEADER_SLOTS = 9
	const val NO_PAGE = -1

	val authoritative = setOf(STORAGE)

	private const val STORAGE = "storage"
	private const val UNOPENED = " — click to open"
	private const val STACKS = "stacks"
	private const val ROWS = "r"
	private const val BLOB = "b"
	private const val NBT_QUOTA = 4L * 1024 * 1024
	private const val ENDER_FIRST_SLOT = 9
	private const val ENDER_LAST_SLOT = 17
	private const val BACKPACK_FIRST_SLOT = 27
	private const val BACKPACK_LAST_SLOT = 44

	private val names = Array(PAGES) {
		if (it < ENDER_PAGES) "Ender Chest #${it + 1}" else "Backpack #${it - ENDER_PAGES + 1}"
	}
	private val unopenedNames = Array(PAGES) { names[it] + UNOPENED }
	private val contents = arrayOfNulls<List<ItemStack>>(PAGES)
	private val stored = arrayOfNulls<String>(PAGES)
	private val decoded = BooleanArray(PAGES)
	private val present = BooleanArray(PAGES)
	private val dirty = BooleanArray(PAGES)
	private val rowCounts = IntArray(PAGES)

	private var root = JsonObject()
	private var store: ConfigStore? = null
	private var scope: CoroutineScope? = null
	private var key: String? = null

	private val missingMarkers: Set<Item> by lazy {
		setOf(Items.STAINED_GLASS_PANE.red(), Items.STAINED_GLASS_PANE.brown(), Items.DYE.gray())
	}

	fun install(store: ConfigStore, scope: CoroutineScope) {
		this.store = store
		this.scope = scope
		root = store.load().obj(STORAGE) ?: JsonObject()
	}

	fun overviewPage(slot: Int): Int = when (slot) {
		in ENDER_FIRST_SLOT..ENDER_LAST_SLOT -> slot - ENDER_FIRST_SLOT
		in BACKPACK_FIRST_SLOT..BACKPACK_LAST_SLOT -> slot - BACKPACK_FIRST_SLOT + ENDER_PAGES
		else -> NO_PAGE
	}

	fun name(page: Int): String = names[page]

	fun unopenedName(page: Int): String = unopenedNames[page]

	fun command(page: Int): String =
		if (page < ENDER_PAGES) "enderchest ${page + 1}" else "backpack ${page - ENDER_PAGES + 1}"

	fun page(index: Int): List<ItemStack>? {
		follow()
		if (index !in 0 until PAGES) return null
		contents[index]?.let { return it }
		if (decoded[index]) return null
		decoded[index] = true
		val items = stored[index]?.let(::decode) ?: return null
		contents[index] = items
		return items
	}

	fun exists(index: Int): Boolean {
		follow()
		return present[index] || contents[index] != null || stored[index] != null
	}

	fun rows(index: Int): Int = rowCounts[index]

	fun capture(page: Int, slots: List<ItemStack>, save: Boolean) {
		follow()
		if (page !in 0 until PAGES || slots.size <= HEADER_SLOTS) return
		val items = ArrayList<ItemStack>(slots.size - HEADER_SLOTS)
		for (index in HEADER_SLOTS until slots.size) items += slots[index].copy()
		contents[page] = items
		rowCounts[page] = items.size / ROW_WIDTH
		decoded[page] = true
		present[page] = true
		if (save) persist(page, items) else dirty[page] = true
	}

	fun flush() {
		for (page in 0 until PAGES) {
			if (!dirty[page]) continue
			dirty[page] = false
			contents[page]?.let { persist(page, it) }
		}
	}

	fun observeOverview(slots: List<ItemStack>) {
		follow()
		for (slot in slots.indices) {
			val page = overviewPage(slot)
			if (page == NO_PAGE) continue
			val stack = slots[slot]
			if (stack.isEmpty) continue
			if (stack.item in missingMarkers) forget(page) else present[page] = true
		}
	}

	private fun forget(page: Int) {
		present[page] = false
		if (contents[page] == null && stored[page] == null) return
		contents[page] = null
		stored[page] = null
		rowCounts[page] = 0
		dirty[page] = false
		decoded[page] = true
		persist(page, null)
	}

	private fun persist(page: Int, items: List<ItemStack>?) {
		val profile = key ?: return
		val target = store ?: return
		val registry = Minecraft.getInstance().connection?.registryAccess() ?: return
		val rows = items?.size?.div(ROW_WIDTH) ?: 0
		scope?.launch {
			val text = items?.let { encode(it, registry) }
			synchronized(this@StorageSnapshots) {
				val pages = root.obj(profile) ?: JsonObject().also { root.add(profile, it) }
				if (text == null) {
					pages.remove(page.toString())
				} else {
					pages.add(
						page.toString(),
						JsonObject().also {
							it.addProperty(ROWS, rows)
							it.addProperty(BLOB, text)
						}
					)
				}
				target.save(JsonObject().also { it.add(STORAGE, root.deepCopy()) })
			}
		}
	}

	private fun follow() {
		val profile = ProfileHooks.profile ?: return
		if (profile == key) return
		flush()
		key = profile
		contents.fill(null)
		stored.fill(null)
		decoded.fill(false)
		present.fill(false)
		rowCounts.fill(0)
		synchronized(this) {
			val pages = root.obj(profile) ?: return
			for (index in 0 until PAGES) {
				val entry = pages.obj(index.toString()) ?: continue
				stored[index] = entry.text(BLOB)
				rowCounts[index] = entry.int(ROWS)
			}
		}
	}

	private fun encode(items: List<ItemStack>, registry: HolderLookup.Provider): String? {
		val ops = registry.createSerializationContext(NbtOps.INSTANCE)
		val list = ItemStack.OPTIONAL_CODEC.listOf().encodeStart(ops, items).result().orElse(null) as? ListTag
			?: return null
		return ByteArrayOutputStream().use { bytes ->
			NbtIo.writeCompressed(CompoundTag().also { it.put(STACKS, list) }, bytes)
			Base64.getEncoder().encodeToString(bytes.toByteArray())
		}
	}

	private fun decode(blob: String): List<ItemStack>? {
		val registry = Minecraft.getInstance().connection?.registryAccess() ?: return null
		return runCatching {
			val root = NbtIo.readCompressed(
				ByteArrayInputStream(Base64.getDecoder().decode(blob)),
				NbtAccounter.create(NBT_QUOTA)
			)
			val ops = registry.createSerializationContext(NbtOps.INSTANCE)
			ItemStack.OPTIONAL_CODEC.listOf().parse(ops, root.getListOrEmpty(STACKS)).result().orElse(null)
		}.getOrNull()
	}
}
