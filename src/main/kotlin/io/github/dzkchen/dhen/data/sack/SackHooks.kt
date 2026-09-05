package io.github.dzkchen.dhen.data.sack

import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.world.item.ItemStack

internal object SackHooks : GuardedHooks<SackHooks.Channels> {
	override val feed = "Sack contents"

	override val failsafe = Failsafe("Dhen {} failed, its sack contents are off until restart")

	private var channels: Channels? = null
	private var subscriptions: Array<Handle> = emptyArray()

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels()
		subscriptions = arrayOf(
			bus.subscribe<ContainerReadyEvent>(BEFORE_FEATURES) { opened(it.title.string, it.stacks) },
			bus.subscribe<ContainerUpdatedEvent>(BEFORE_FEATURES) { opened(it.title.string, it.stacks) },
			bus.subscribe<ContainerClosedEvent>(BEFORE_FEATURES) { closed() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		SackState.leaveSack()
	}

	override fun bound() = channels

	private fun opened(title: String, stacks: List<ItemStack>) =
		guarded("sack menu") { it.opened(withoutCodes(title), stacks) }

	private fun closed() = guarded("sack close") { it.closed() }

	internal class Channels(private val inSkyBlock: () -> Boolean = { SkyBlockLocation.inSkyBlock }) {
		private val amounts = HashMap<String, Long>()

		fun opened(title: String, stacks: List<ItemStack>) {
			if (!inSkyBlock() || !(SackMenu.isSack(title) || SackMenu.isSackOfSacks(title))) {
				closed()
				return
			}
			SackState.enterSack(title)
			if (SackMenu.isSackOfSacks(title)) return
			SackMenu.read(title, stacks, SackState.editableRows())
			amounts.clear()
			for (row in SackState.rows) {
				val parts = row.parts
				val ids = row.partIds
				if (parts != null && ids != null) {
					for (index in ids.indices) if (parts[index] != UNREPORTED) amounts[ids[index]] = parts[index]
				} else {
					amounts[row.marketId] = row.stored
				}
			}
			SackState.record(amounts)
			SackState.markFull(SackState.rows)
		}

		fun closed() {
			SackState.leaveSack()
		}
	}
}
