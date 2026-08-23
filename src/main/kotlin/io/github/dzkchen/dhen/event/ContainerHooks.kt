package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

internal object ContainerHooks {
	private val failsafe = Failsafe("Dhen {} failed, its container events are off until restart")

	private val slotCounts: Map<MenuType<*>, Int> = mapOf(
		MenuType.ANVIL to 3,
		MenuType.BEACON to 1,
		MenuType.BLAST_FURNACE to 3,
		MenuType.BREWING_STAND to 5,
		MenuType.CARTOGRAPHY_TABLE to 2,
		MenuType.CRAFTER_3x3 to 9,
		MenuType.CRAFTING to 9,
		MenuType.ENCHANTMENT to 2,
		MenuType.FURNACE to 3,
		MenuType.GENERIC_3x3 to 9,
		MenuType.GENERIC_9x1 to 9,
		MenuType.GENERIC_9x2 to 18,
		MenuType.GENERIC_9x3 to 27,
		MenuType.GENERIC_9x4 to 36,
		MenuType.GENERIC_9x5 to 45,
		MenuType.GENERIC_9x6 to 54,
		MenuType.GRINDSTONE to 3,
		MenuType.HOPPER to 5,
		MenuType.LECTERN to 1,
		MenuType.LOOM to 3,
		MenuType.MERCHANT to 3,
		MenuType.SHULKER_BOX to 27,
		MenuType.SMITHING to 3,
		MenuType.SMOKER to 3,
		MenuType.STONECUTTER to 1
	)

	@Volatile
	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		subscriptions = arrayOf(
			bus.subscribe<PacketReceiveEvent.Post> { received(it.packet) },
			bus.subscribe<PacketSendEvent> { sent(it.packet) },
			bus.subscribe<WorldChangeEvent> { forgetTrackedContainer() }
		)
	}

	fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
	}

	fun active(): Boolean = channels != null

	fun tick() = guarded("container tick") { it.flush() }

	private fun forgetTrackedContainer() = guarded("container world change") { it.forget() }

	private fun received(packet: Packet<*>) = guarded("container packet") { it.received(packet) }

	private fun sent(packet: Packet<*>) = guarded("container close") { it.sent(packet) }

	private inline fun guarded(label: String, block: (Channels) -> Unit) {
		val channels = channels ?: return
		try {
			block(channels)
		} catch (throwable: Throwable) {
			uninstall()
			failsafe.fail(label, throwable)
		}
	}

	private class Channels(bus: EventBus) {
		private val readies = bus.type<ContainerReadyEvent>()
		private val updates = bus.type<ContainerUpdatedEvent>()
		private val closes = bus.type<ContainerClosedEvent>()
		private val tracker = ContainerTracker()
		private var title: Component = Component.empty()
		private var stacks: MutableList<ItemStack> = ArrayList()
		private var updated: ContainerUpdatedEvent? = null

		fun received(packet: Packet<*>) {
			when (packet) {
				is ClientboundOpenScreenPacket -> opened(packet)
				is ClientboundContainerSetContentPacket ->
					signal(tracker.contentSet(packet.containerId(), packet.items().size))
				is ClientboundContainerSetSlotPacket -> signal(tracker.slotSet(packet.containerId, packet.slot))
				is ClientboundContainerClosePacket -> closed(packet.containerId, false)
			}
		}

		fun sent(packet: Packet<*>) {
			if (packet is ServerboundContainerClosePacket) closed(packet.containerId, false)
		}

		fun forget() {
			tracker.reset()
			title = Component.empty()
			stacks = ArrayList()
			updated = null
		}

		fun flush() {
			if (!tracker.flushUpdate()) return
			val event = updated ?: return
			if (refreshed()) updates.dispatch(event)
		}

		private fun opened(packet: ClientboundOpenScreenPacket) {
			closed(tracker.windowId, packet.title == title)
			title = packet.title
			val slotCount = slotCounts[packet.type]
			if (slotCount == null) {
				tracker.reset()
				return
			}
			stacks = ArrayList<ItemStack>(slotCount).apply { repeat(slotCount) { add(ItemStack.EMPTY) } }
			tracker.opened(packet.containerId, slotCount)
		}

		private fun closed(windowId: Int, reopening: Boolean) {
			val closing = tracker.windowId
			if (!tracker.closed(windowId)) return
			updated = null
			closes.dispatch(ContainerClosedEvent(title, closing, reopening))
		}

		private fun signal(outcome: ContainerSignal) {
			if (outcome != ContainerSignal.READY || !refreshed()) return
			updated = ContainerUpdatedEvent(title, tracker.windowId, tracker.slotCount, stacks)
			readies.dispatch(ContainerReadyEvent(title, tracker.windowId, tracker.slotCount, stacks))
		}

		private fun refreshed(): Boolean {
			val menu = Minecraft.getInstance().player?.containerMenu ?: return false
			if (menu.containerId != tracker.windowId || menu.slots.size < tracker.slotCount) return false
			for (index in 0 until tracker.slotCount) stacks[index] = menu.slots[index].item
			return true
		}
	}
}
