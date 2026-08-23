package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.Entity

internal object RenderHooks : Hooks {
	override val feed = "Entity render"

	private val failsafe = Failsafe("Dhen {} failed, its render events are off until restart")

	private var channels: Channels? = null

	private var disconnects: Handle? = null

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		disconnects = bus.subscribe<WorldChangeEvent> { change ->
			if (change.phase == WorldChange.DISCONNECT) guarded("entity render world change") { it.forget(); false }
		}
	}

	override fun uninstall() {
		disconnects?.unsubscribe()
		disconnects = null
		channels = null
	}

	override fun active(): Boolean = channels != null

	@JvmStatic
	fun entityOutline(entity: Entity, vanillaOutline: Int): Int {
		val channels = channels ?: return vanillaOutline
		return try {
			channels.glow(entity, vanillaOutline)
		} catch (throwable: Throwable) {
			latchOff("entity glow", throwable)
			vanillaOutline
		}
	}

	@JvmStatic
	fun entityRenderCancelled(entity: Entity): Boolean = guarded("entity render") { it.rendering(entity) }

	@JvmStatic
	fun bossBarCancelled(bossBar: BossEvent): Boolean = guarded("boss bar") { it.bossBar(bossBar) }

	private inline fun guarded(label: String, block: (Channels) -> Boolean): Boolean {
		val channels = channels ?: return false
		return try {
			block(channels)
		} catch (throwable: Throwable) {
			latchOff(label, throwable)
			false
		}
	}

	private fun latchOff(label: String, throwable: Throwable) {
		uninstall()
		failsafe.fail(label, throwable)
	}

	private class Channels(bus: EventBus) {
		private val glows = bus.type<EntityGlowEvent>()
		private val renders = bus.type<EntityRenderEvent>()
		private val bossBars = bus.type<BossBarUpdateEvent>()
		private val glowEvents = ReusableEvent(::EntityGlowEvent)
		private val renderEvents = ReusableEvent(::EntityRenderEvent)
		private val bossBarEvents = ReusableEvent(::BossBarUpdateEvent)

		fun glow(entity: Entity, vanillaOutline: Int): Int {
			if (!glows.hasSubscribers) return vanillaOutline
			val event = glowEvents.borrow()
			event.entity = entity
			event.seed(vanillaOutline)
			return try {
				glows.dispatch(event)
				event.outline()
			} finally {
				glowEvents.release(event)
			}
		}

		fun rendering(entity: Entity): Boolean {
			if (!renders.hasSubscribers) return false
			val event = renderEvents.borrow()
			event.cancelled = false
			event.entity = entity
			return try {
				renders.dispatch(event)
				event.cancelled
			} finally {
				renderEvents.release(event)
			}
		}

		fun forget() {
			glowEvents.forget { it.forget() }
			renderEvents.forget { it.forget() }
		}

		fun bossBar(bossBar: BossEvent): Boolean {
			if (!bossBars.hasSubscribers) return false
			val event = bossBarEvents.borrow()
			event.cancelled = false
			event.bossBar = bossBar
			return try {
				bossBars.dispatch(event)
				event.cancelled
			} finally {
				bossBarEvents.release(event)
			}
		}
	}
}
