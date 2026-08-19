package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

internal object WorldRenderHooks {
	private val failsafe = Failsafe("Dhen {} failed, its world render events are off until restart")

	@Volatile
	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	fun uninstall() {
		channels = null
	}

	fun active(): Boolean = channels != null

	fun render(context: LevelRenderContext) {
		val channels = channels ?: return
		try {
			channels.render(context)
		} catch (throwable: Throwable) {
			this.channels = null
			failsafe.fail("world render", throwable)
		}
	}

	private class Channels(bus: EventBus) {
		private val renders = bus.type<WorldRenderEvent>()
		private val events = ReusableEvent(::WorldRenderEvent)

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
