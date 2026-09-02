package io.github.dzkchen.dhen.data.pickup

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SidebarField
import io.github.dzkchen.dhen.data.SidebarValues
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.ItemPickupEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.event.PickupSource
import io.github.dzkchen.dhen.event.PurseChangeEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.NO_DIGITS
import io.github.dzkchen.dhen.util.digits
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import java.util.regex.Matcher
import java.util.regex.Pattern

internal object PickupHooks : GuardedHooks<PickupHooks.Channels> {
	override val feed = "Pickup log"

	override val failsafe = Failsafe("Dhen {} failed, its pickup log is off until restart")

	private const val ITEM_MARKER = " item"
	private const val SACKS = "Sacks"

	private var channels: Channels? = null
	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		subscriptions = arrayOf(
			bus.subscribe<PacketReceiveEvent.Post>(BEFORE_FEATURES) { received(it) },
			bus.subscribe<ChatReceiveEvent>(BEFORE_FEATURES) { sacked(it) },
			bus.subscribe<WorldChangeEvent> { reset() },
			bus.subscribe<IslandChangeEvent> { if (it.resetsWorldState) reset() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
	}

	override fun bound() = channels

	internal fun sackItemMessage(stripped: String): Boolean {
		val marker = stripped.indexOf(ITEM_MARKER)
		return marker > 0 && stripped.lastIndexOf(SACKS, marker) >= 0
	}

	private fun received(event: PacketReceiveEvent.Post) = guarded("pickup slot update") { channels ->
		if (event.packet !is ClientboundContainerSetSlotPacket) return@guarded
		if (!SkyBlockLocation.inSkyBlock || SkyBlockLocation.island == Island.NONE) return@guarded
		val player = Minecraft.getInstance().player ?: return@guarded
		channels.slotUpdated(
			player.inventory.nonEquipmentItems,
			player.containerMenu.carried,
			SidebarValues.number(SidebarField.PURSE)
		)
	}

	private fun sacked(event: ChatReceiveEvent) = guarded("pickup sack message") { channels ->
		if (!SkyBlockLocation.inSkyBlock || !sackItemMessage(event.stripped)) return@guarded
		channels.sacked(event.text)
	}

	private fun reset() = guarded("pickup reset") { it.reset() }

	internal class Channels(bus: EventBus) {
		private val pickups = bus.type<ItemPickupEvent>()
		private val purses = bus.type<PurseChangeEvent>()
		private val slots = SkyBlockItems.memo(Inventory.INVENTORY_SIZE)
		private val sackAmounts: Matcher = Pattern.compile("\\+([\\d,]+) ([^(]+)").matcher("")
		private var previous = InventorySnapshot()
		private var current = InventorySnapshot()
		private var primed = false
		private var purse = NO_DIGITS

		fun slotUpdated(items: List<ItemStack>, carried: ItemStack, sidebarPurse: Long) {
			purseChanged(sidebarPurse)
			if (!carried.isEmpty) return
			capture(items)
			if (primed) diff() else primed = true
			val held = previous
			previous = current
			current = held
		}

		fun sacked(message: Component) {
			val parts = message.siblings
			for (index in parts.indices) {
				val part = parts[index]
				if (!part.string.contains(ITEM_MARKER)) continue
				val hover = part.style.hoverEvent as? HoverEvent.ShowText ?: continue
				sackedAmounts(hover.value().string)
			}
		}

		fun reset() {
			previous.clear()
			current.clear()
			primed = false
			purse = NO_DIGITS
		}

		private fun sackedAmounts(hover: String) {
			sackAmounts.reset(hover)
			while (sackAmounts.find()) {
				val amount = digits(sackAmounts.group(1))
				if (amount <= 0L) continue
				val name = sackAmounts.group(2).trim()
				pickups.dispatch(ItemPickupEvent(name, name, "", 0L, amount.toInt(), PickupSource.SACK))
			}
		}

		private fun capture(items: List<ItemStack>) {
			current.clear()
			for (slot in items.indices) {
				if (slot == MENU_SLOT) continue
				val stack = items[slot]
				if (stack.isEmpty) continue
				val item = slots.of(slot, stack)
				if (item.id == ENCHANTED_BOOK && chimera(stack)) {
					current.add(CHIMERA, CHIMERA, CHIMERA_NAME, item.uuid, item.timestamp, stack.count)
					continue
				}
				val key = item.uuid.ifEmpty { item.id }
				current.add(key, item.id, stack.hoverName, item.uuid, item.timestamp, stack.count)
			}
		}

		private fun chimera(stack: ItemStack): Boolean {
			val lore = SkyBlockItems.lore(stack)
			for (index in lore.indices) if (lore[index].string.contains(CHIMERA_LORE)) return true
			return false
		}

		private fun diff() {
			for (index in 0 until current.size) {
				val was = previous.indexOf(current.key(index))
				val delta = if (was < 0) current.count(index) else current.count(index) - previous.count(was)
				if (delta != 0) publish(current, index, delta)
			}
			for (index in 0 until previous.size) {
				if (current.indexOf(previous.key(index)) < 0) publish(previous, index, -previous.count(index))
			}
		}

		private fun publish(snapshot: InventorySnapshot, index: Int, delta: Int) {
			pickups.dispatch(
				ItemPickupEvent(
					snapshot.id(index),
					legacyCodes(snapshot.name(index)),
					snapshot.uuid(index),
					snapshot.createdAt(index),
					delta,
					PickupSource.INVENTORY
				)
			)
		}

		private fun purseChanged(sidebarPurse: Long) {
			if (sidebarPurse == NO_DIGITS) return
			val was = purse
			purse = sidebarPurse
			if (was == NO_DIGITS || was == sidebarPurse) return
			purses.dispatch(PurseChangeEvent(sidebarPurse - was))
		}
	}

	private const val MENU_SLOT = 8
	private const val ENCHANTED_BOOK = "ENCHANTED_BOOK"
	private const val CHIMERA = "CHIMERA"
	private const val CHIMERA_LORE = "Chimera"
	private val CHIMERA_NAME: Component =
		Component.literal("Chimera").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
}

internal class InventorySnapshot {
	private val keys = arrayOfNulls<String>(CAPACITY)
	private val ids = arrayOfNulls<String>(CAPACITY)
	private val names = arrayOfNulls<Component>(CAPACITY)
	private val uuids = arrayOfNulls<String>(CAPACITY)
	private val createdAt = LongArray(CAPACITY)
	private val counts = IntArray(CAPACITY)

	var size = 0
		private set

	fun clear() {
		size = 0
	}

	fun add(key: String, id: String, name: Component, uuid: String, createdAt: Long, count: Int) {
		val at = indexOf(key)
		if (at >= 0) {
			counts[at] += count
			return
		}
		if (size == CAPACITY) return
		keys[size] = key
		ids[size] = id
		names[size] = name
		uuids[size] = uuid
		this.createdAt[size] = createdAt
		counts[size] = count
		size++
	}

	fun indexOf(key: String): Int {
		for (index in 0 until size) if (keys[index] == key) return index
		return -1
	}

	fun key(index: Int): String = keys[index]!!

	fun id(index: Int): String = ids[index]!!

	fun name(index: Int): Component = names[index]!!

	fun uuid(index: Int): String = uuids[index]!!

	fun createdAt(index: Int): Long = createdAt[index]

	fun count(index: Int): Int = counts[index]

	private companion object {
		const val CAPACITY = 36
	}
}
