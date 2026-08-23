package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

internal object WorldRenderHooks : Hooks {
	override val feed = "World render"

	private val failsafe = Failsafe("Dhen {} failed, its world render events are off until restart")

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

	override fun active(): Boolean = channels != null

	fun render(context: LevelRenderContext) = guarded("world render") { it.render(context) }

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
