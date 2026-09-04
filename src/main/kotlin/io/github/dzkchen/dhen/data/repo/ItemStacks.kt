package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.Dynamic
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.obj
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.RegistryOps
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.TooltipDisplay
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.floor
import kotlin.streams.asSequence

private const val LEGACY_DATA_VERSION = 99
private const val EXTRA_ATTRIBUTES = "ExtraAttributes"
private const val ENCHANTED_BOOK = "ENCHANTED_BOOK"
private const val OVERLAYS = "itemsOverlay"
private const val SNBT = ".snbt"
private const val LEVEL_PLACEHOLDER = "{LVL}"
private const val RANGE = " ➡ "

private val RARITIES = arrayOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
private val vanillaLookup = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { VanillaRegistries.createLookup() }

internal class PetNumbers private constructor(private val byId: Map<String, List<Pair<String, String>>>) {
	fun placeholders(id: String): List<Pair<String, String>> = byId[id].orEmpty()

	companion object {
		val EMPTY = PetNumbers(emptyMap())

		fun read(constants: Path): PetNumbers {
			val file = constants.resolve("petnums.json")
			if (!Files.isRegularFile(file)) return EMPTY
			return try {
				val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
				val byId = HashMap<String, List<Pair<String, String>>>(json.size())
				for ((pet, rarities) in json.entrySet()) {
					val listed = rarities as? JsonObject ?: continue
					for ((rarity, levels) in listed.entrySet()) {
						val ordinal = RARITIES.indexOf(rarity.uppercase(Locale.ROOT))
						val range = levels as? JsonObject ?: continue
						if (ordinal < 0) continue
						byId["${pet.uppercase(Locale.ROOT)};$ordinal"] = placeholders(range)
					}
				}
				PetNumbers(byId)
			} catch (throwable: Throwable) {
				log.warn("Dhen could not read the repo pet numbers", throwable)
				EMPTY
			}
		}

		private fun placeholders(levels: JsonObject): List<Pair<String, String>> {
			val ordered = levels.keySet().mapNotNull { it.toIntOrNull() }.sorted()
			val low = ordered.firstOrNull() ?: return emptyList()
			val high = ordered.last()
			val atLow = levels.obj(low.toString()) ?: return emptyList()
			val atHigh = levels.obj(high.toString()) ?: return emptyList()
			return buildList {
				add(LEVEL_PLACEHOLDER to "$low$RANGE$high")
				val statsLow = atLow.obj("statNums")
				val statsHigh = atHigh.obj("statNums")
				for (stat in statsLow?.keySet().orEmpty()) {
					add("{$stat}" to span(statsLow?.get(stat).numberOrNull(), statsHigh?.get(stat).numberOrNull()))
				}
				val otherLow = atLow.getAsJsonArray("otherNums") ?: return@buildList
				val otherHigh = atHigh.getAsJsonArray("otherNums")
				for (index in 0 until otherLow.size()) {
					val ceiling = otherHigh?.takeIf { index < it.size() }?.get(index)
					add("{$index}" to span(otherLow[index].numberOrNull(), ceiling.numberOrNull()))
				}
			}
		}

		private fun span(low: Double?, high: Double?): String {
			val start = round(low ?: return "")
			val end = high?.let(::round) ?: return start
			return "$start$RANGE$end"
		}

		private fun round(value: Double): String =
			if (value == floor(value)) value.toLong().toString() else value.toString()
	}
}

internal class StackOverlays private constructor(private val files: Map<String, Path>) {
	fun apply(id: String, stack: ItemStack) {
		val file = files[id] ?: return
		try {
			val tag = TagParser.parseCompoundFully(Files.readString(file))
			val overlay = ItemStack.CODEC.parse(registryOps(), tag).resultOrPartial().orElse(null) ?: return
			if (!overlay.isEmpty) stack.applyComponents(overlay.componentsPatch)
		} catch (throwable: Throwable) {
			log.warn("Dhen could not apply the repo overlay for {}", id, throwable)
		}
	}

	companion object {
		val EMPTY = StackOverlays(emptyMap())

		fun read(root: Path): StackOverlays {
			val overlays = root.resolve(OVERLAYS)
			if (!Files.isDirectory(overlays)) return EMPTY
			val supported = SharedConstants.getCurrentVersion().dataVersion().version()
			return try {
				val files = HashMap<String, Path>()
				Files.list(overlays).use { listing ->
					listing.asSequence()
						.filter(Files::isDirectory)
						.mapNotNull { directory -> directory.fileName.toString().toIntOrNull()?.let { it to directory } }
						.filter { it.first <= supported }
						.sortedBy { it.first }
						.forEach { collect(it.second, files) }
				}
				if (files.isEmpty()) EMPTY else StackOverlays(files)
			} catch (throwable: Throwable) {
				log.warn("Dhen could not read the repo item overlays", throwable)
				EMPTY
			}
		}

		private fun collect(directory: Path, files: MutableMap<String, Path>) {
			Files.list(directory).use { listing ->
				for (file in listing.asSequence()) {
					val name = file.fileName.toString()
					if (name.endsWith(SNBT)) files[name.removeSuffix(SNBT).uppercase(Locale.ROOT)] = file
				}
			}
		}
	}
}

internal fun repoStack(item: RepoItem, pets: PetNumbers): ItemStack {
	val stack = modernized(item) ?: return namedPlaceholder(item.id)
	val placeholders = pets.placeholders(item.id)
	stack.set(DataComponents.CUSTOM_NAME, legacyComponent(injected(item.displayName, placeholders)))
	stack.set(DataComponents.LORE, ItemLore(item.lore.map { legacyComponent(injected(it, placeholders)) }))
	if (stack.`is`(Items.ENCHANTED_BOOK) && item.id.contains(';')) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putString("id", ENCHANTED_BOOK) }
	}
	return stack
}

internal fun namedPlaceholder(id: String): ItemStack =
	ItemStack(Items.BARRIER).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(id)) }

internal fun coinStack(): ItemStack = ItemStack(Items.GOLD_NUGGET).also {
	it.set(DataComponents.ITEM_NAME, Component.literal("Skyblock Coins").withStyle(ChatFormatting.GOLD))
	it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
}

private fun modernized(item: RepoItem): ItemStack? = try {
	val legacy = CompoundTag()
	legacy.put("tag", if (item.nbttag.isBlank()) CompoundTag() else TagParser.parseCompoundFully(withoutListIndices(item.nbttag)))
	legacy.putString("id", item.itemId)
	legacy.putShort("Damage", item.damage.toShort())
	legacy.putInt("Count", 1)
	val fixed = DataFixers.getDataFixer().update(
		References.ITEM_STACK,
		Dynamic<Tag>(registryOps(), legacy),
		LEGACY_DATA_VERSION,
		SharedConstants.getCurrentVersion().dataVersion().version()
	)
	ItemStack.CODEC.parse(fixed).resultOrPartial().orElse(null)?.takeUnless(ItemStack::isEmpty)?.also(::unwrap)
} catch (throwable: Throwable) {
	log.warn("Dhen could not build a stack for the repo item {}", item.id, throwable)
	null
}

private fun unwrap(stack: ItemStack) {
	stack.get(DataComponents.CUSTOM_DATA)?.let { data ->
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data.copyTag().getCompoundOrEmpty(EXTRA_ATTRIBUTES)))
	}
	stack.set(
		DataComponents.TOOLTIP_DISPLAY,
		stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
			.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
			.withHidden(DataComponents.ENCHANTMENTS, true)
	)
}

private fun injected(text: String, placeholders: List<Pair<String, String>>): String {
	if (placeholders.isEmpty() || !text.contains('{')) return text
	var injected = text
	for ((placeholder, value) in placeholders) injected = injected.replace(placeholder, value)
	return injected
}

internal fun withoutListIndices(snbt: String): String {
	var rewritten: StringBuilder? = null
	var quoted = false
	var escaped = false
	var index = 0
	while (index < snbt.length) {
		val character = snbt[index]
		rewritten?.append(character)
		index++
		when {
			escaped -> escaped = false
			character == '\\' -> escaped = true
			character == '"' -> quoted = !quoted
			quoted || (character != '[' && character != ',') -> Unit
			else -> {
				var scan = index
				while (scan < snbt.length && snbt[scan].isWhitespace()) scan++
				val digits = scan
				while (scan < snbt.length && snbt[scan].isDigit()) scan++
				if (scan == digits || scan >= snbt.length || snbt[scan] != ':') continue
				if (rewritten == null) rewritten = StringBuilder(snbt.length).append(snbt, 0, index)
				index = scan + 1
			}
		}
	}
	return rewritten?.toString() ?: snbt
}

private fun registryOps(): RegistryOps<Tag> = lookup().createSerializationContext(NbtOps.INSTANCE)

private fun lookup(): HolderLookup.Provider {
	val client: Minecraft? = Minecraft.getInstance()
	return client?.connection?.registryAccess() ?: vanillaLookup.value
}
