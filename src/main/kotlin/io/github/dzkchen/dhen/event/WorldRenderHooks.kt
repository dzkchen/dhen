package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState

internal object WorldRenderHooks : GuardedHooks<WorldRenderHooks.Channels> {
	override val feed = "World render"

	override val failsafe = Failsafe("Dhen {} failed, its world render events are off until restart")

	private var channels: Channels? = null

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
	}

	override fun uninstall() {
		channels = null
	}

	override fun bound() = channels

	fun render(context: LevelRenderContext) = guarded("world render") { it.render(context) }

	fun blockOutline(context: LevelRenderContext, outline: BlockOutlineRenderState): Boolean =
		guarded("block outline", true) { it.blockOutline(context, outline) }

	internal class Channels(bus: EventBus) {
		private val renders = bus.type<WorldRenderEvent>()
		private val outlines = bus.type<BlockOutlineEvent>()
		private val events = ReusableEvent(::WorldRenderEvent, WorldRenderEvent::forget)
		private val outlineEvents = ReusableEvent(::BlockOutlineEvent, BlockOutlineEvent::forget)

		fun render(context: LevelRenderContext) {
			if (!renders.hasSubscribers) return
			val event = events.borrow()
			event.seed(context)
			try {
				renders.dispatch(event)
			} finally {
				events.release(event)
			}
		}

		fun blockOutline(context: LevelRenderContext, outline: BlockOutlineRenderState): Boolean {
			if (!outlines.hasSubscribers) return true
			val event = outlineEvents.borrow()
			event.seed(context, outline.pos(), outline.shape())
			return try {
				outlines.dispatch(event)
				event.drawsVanillaOutline
			} finally {
				outlineEvents.release(event)
			}
		}
	}
}
