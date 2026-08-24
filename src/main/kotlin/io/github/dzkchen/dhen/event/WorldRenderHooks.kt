package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

internal object WorldRenderHooks : GuardedHooks<WorldRenderHooks.Channels> {
	override val feed = "World render"

	override val failsafe = Failsafe("Dhen {} failed, its world render events are off until restart")

	private var channels: Channels? = null

	private var disconnects: Handle? = null

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		disconnects = bus.subscribe<WorldChangeEvent> { change ->
			if (change.phase == WorldChange.DISCONNECT) guarded("world render world change") { it.forget() }
		}
	}

	override fun uninstall() {
		disconnects?.unsubscribe()
		disconnects = null
		channels = null
	}

	override fun bound() = channels

	fun render(context: LevelRenderContext) = guarded("world render") { it.render(context) }

	internal class Channels(bus: EventBus) {
		private val renders = bus.type<WorldRenderEvent>()
		private val events = ReusableEvent(::WorldRenderEvent)

		fun forget() = events.forget { it.forget() }

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
	}
}
