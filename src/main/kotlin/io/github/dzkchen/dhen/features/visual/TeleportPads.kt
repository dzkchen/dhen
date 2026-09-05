package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.EntityNameTagEvent
import io.github.dzkchen.dhen.event.EntityRenderEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import java.util.Arrays
import java.util.regex.Pattern

object TeleportPads : Module(
	name = "Teleport Pads",
	category = Category.VISUAL,
	description = "Shortens the floating label over a teleport pad to its destination, and hides pads with none."
) {
	private val warpName = Pattern.compile(WARP_NAME).matcher("")
	private val noDestination = Pattern.compile(NO_DESTINATION).matcher("")

	private val ids = IntArray(CAPACITY) { NO_ENTITY }
	private val sources = arrayOfNulls<Component>(CAPACITY)
	private val shortened = arrayOfNulls<Component>(CAPACITY)
	private val empty = BooleanArray(CAPACITY)

	private var evictionHand = 0

	init {
		on<EntityNameTagEvent> { labelled(it) }
		on<EntityRenderEvent> { rendering(it) }
		on<WorldChangeEvent> { forget() }
	}

	override fun onDisabled() = forget()

	private fun forget() {
		Arrays.fill(ids, NO_ENTITY)
		Arrays.fill(sources, null)
		Arrays.fill(shortened, null)
	}

	private fun labelled(event: EntityNameTagEvent) {
		val entity = event.entity
		if (!onOwnIsland(entity)) return
		val name = entity.customName ?: return
		event.nameTag = shortened[slotFor(entity.id, name)] ?: event.nameTag
	}

	private fun rendering(event: EntityRenderEvent) {
		val entity = event.entity
		if (!onOwnIsland(entity)) return
		val name = entity.customName ?: return
		if (empty[slotFor(entity.id, name)]) event.cancelled = true
	}

	private fun slotFor(entityId: Int, name: Component): Int {
		val home = entityId and MASK
		for (step in 0 until PROBE) {
			val slot = (home + step) and MASK
			if (ids[slot] == entityId && sources[slot] === name) return slot
			if (ids[slot] == entityId || ids[slot] == NO_ENTITY) return read(slot, entityId, name)
		}
		return read((home + (evictionHand++ and (PROBE - 1))) and MASK, entityId, name)
	}

	private fun read(slot: Int, entityId: Int, name: Component): Int {
		val styled = legacyCodes(name)
		ids[slot] = entityId
		sources[slot] = name
		empty[slot] = noDestination.reset(styled).matches()
		shortened[slot] = if (warpName.reset(styled).matches()) DhenType.component(warpName.group("name")) else null
		return slot
	}

	private fun onOwnIsland(entity: Entity): Boolean =
		SkyBlockLocation.island == Island.PRIVATE_ISLAND && entity is ArmorStand

	private const val CAPACITY = 64
	private const val MASK = CAPACITY - 1
	private const val PROBE = 4
	private const val NO_ENTITY = -1
	private const val WARP_NAME = "§.✦ §aWarp To (?<name>.*)"
	private const val NO_DESTINATION = "§.✦ §cNo Destination"
}
