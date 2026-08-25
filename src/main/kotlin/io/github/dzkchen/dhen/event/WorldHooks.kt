package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState

internal object WorldHooks : GuardedHooks<WorldHooks.Channels> {
	override val feed = "World"

	override val failsafe = Failsafe("Dhen {} failed, its world events are off until restart")

	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	override fun uninstall() {
		channels = null
	}

	override fun bound() = channels

	fun worldChanged(phase: WorldChange) = guarded("world change") { it.changed(phase) }

	@JvmStatic
	fun blockChanged(pos: BlockPos, oldState: BlockState, newState: BlockState) =
		guarded("block change") { it.blockChanged(pos, oldState, newState) }

	@JvmStatic
	fun entityUnloaded(entity: Entity) = guarded("entity unload") { it.entityUnloaded(entity) }

	internal class Channels(bus: EventBus) {
		private val changes = bus.type<WorldChangeEvent>()
		private val blocks = bus.type<BlockChangeEvent>()
		private val unloads = bus.type<EntityUnloadEvent>()
		private val blockEvents = ReusableEvent(::BlockChangeEvent, BlockChangeEvent::forget)

		fun changed(phase: WorldChange) = changes.dispatch(WorldChangeEvent(phase))

		fun blockChanged(pos: BlockPos, oldState: BlockState, newState: BlockState) {
			if (!blocks.hasSubscribers) return
			val event = blockEvents.borrow()
			event.pos = pos
			event.oldState = oldState
			event.newState = newState
			try {
				blocks.dispatch(event)
			} finally {
				blockEvents.release(event)
			}
		}

		fun entityUnloaded(entity: Entity) {
			if (unloads.hasSubscribers) unloads.dispatch(EntityUnloadEvent(entity))
		}
	}
}
