package io.github.dzkchen.dhen.features.inventory

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.numericInts
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.texts

internal object ContainerState {
	const val NO_SLOT = -1
	const val MENU_SLOTS = 46
	const val INVENTORY_SLOTS = 41
	const val HOTBAR_FIRST = 36
	const val HOTBAR_LAST = 44

	val authoritative = setOf(BINDS)

	val protectedUuids = HashSet<String>()
	val protectedIds = HashSet<String>()

	private val partners = IntArray(MENU_SLOTS) { NO_SLOT }

	private var locked = 0L

	private var store: ConfigStore? = null

	fun install(store: ConfigStore) {
		this.store = store
		read(store.load())
	}

	fun isLocked(containerSlot: Int): Boolean =
		containerSlot in 0 until INVENTORY_SLOTS && locked and (1L shl containerSlot) != 0L

	fun toggleLock(containerSlot: Int): Boolean {
		if (containerSlot !in 0 until INVENTORY_SLOTS) return false
		locked = locked xor (1L shl containerSlot)
		save()
		return isLocked(containerSlot)
	}

	fun partner(menuSlot: Int): Int =
		if (menuSlot in 0 until MENU_SLOTS) partners[menuSlot] else NO_SLOT

	fun bind(inventorySlot: Int, hotbarSlot: Int) {
		if (inventorySlot !in 0 until MENU_SLOTS || hotbarSlot !in 0 until MENU_SLOTS) return
		clear(inventorySlot)
		clear(hotbarSlot)
		partners[inventorySlot] = hotbarSlot
		partners[hotbarSlot] = inventorySlot
		save()
	}

	fun unbind(menuSlot: Int) {
		if (partner(menuSlot) == NO_SLOT) return
		clear(menuSlot)
		save()
	}

	fun protect(key: String, byUuid: Boolean): Boolean {
		val target = if (byUuid) protectedUuids else protectedIds
		val added = target.add(key)
		if (!added) target.remove(key)
		save()
		return added
	}

	fun save() {
		store?.save(snapshot())
	}

	internal fun snapshot(): JsonObject {
		val document = JsonObject()
		document.add(PROTECTED_UUIDS, strings(protectedUuids))
		document.add(PROTECTED_IDS, strings(protectedIds))
		val slots = JsonArray()
		for (slot in 0 until INVENTORY_SLOTS) if (isLocked(slot)) slots.add(slot)
		document.add(LOCKED_SLOTS, slots)
		val binds = JsonObject()
		for (slot in 0 until MENU_SLOTS) {
			val held = partners[slot]
			if (held in HOTBAR_FIRST..HOTBAR_LAST) binds.addProperty(slot.toString(), held)
		}
		document.add(BINDS, binds)
		return document
	}

	internal fun read(document: JsonObject) {
		protectedUuids.clear()
		protectedIds.clear()
		partners.fill(NO_SLOT)
		locked = 0L
		protectedUuids += document.array(PROTECTED_UUIDS).texts()
		protectedIds += document.array(PROTECTED_IDS).texts()
		document.array(LOCKED_SLOTS)?.forEach { element ->
			val slot = element.numberOrNull()?.toInt() ?: return@forEach
			if (slot in 0 until INVENTORY_SLOTS) locked = locked or (1L shl slot)
		}
		for ((key, hotbarSlot) in document.obj(BINDS).numericInts()) {
			val inventorySlot = key.toIntOrNull() ?: continue
			if (hotbarSlot !in HOTBAR_FIRST..HOTBAR_LAST) continue
			if (inventorySlot !in 0 until MENU_SLOTS) continue
			if (partners[inventorySlot] != NO_SLOT || partners[hotbarSlot] != NO_SLOT) continue
			partners[inventorySlot] = hotbarSlot
			partners[hotbarSlot] = inventorySlot
		}
	}

	private fun clear(menuSlot: Int) {
		val held = partners[menuSlot]
		if (held == NO_SLOT) return
		partners[menuSlot] = NO_SLOT
		partners[held] = NO_SLOT
	}

	private fun strings(values: Set<String>): JsonArray {
		val array = JsonArray()
		for (value in values) array.add(value)
		return array
	}
}

private const val PROTECTED_UUIDS = "protectedUuids"
private const val PROTECTED_IDS = "protectedIds"
private const val LOCKED_SLOTS = "lockedSlots"
private const val BINDS = "binds"
