package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.Entity

internal object RenderHooks : GuardedHooks<RenderHooks.Channels> {
	override val feed = "Entity render"

	override val failsafe = Failsafe("Dhen {} failed, its render events are off until restart")

	private var channels: Channels? = null

	private var disconnects: Handle? = null

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		disconnects = bus.subscribe<WorldChangeEvent> { change ->
			if (change.phase == WorldChange.DISCONNECT) guarded("entity render world change") { it.forget() }
		}
	}

	override fun uninstall() {
		disconnects?.unsubscribe()
		disconnects = null
		channels = null
	}

	override fun bound() = channels

	@JvmStatic
	fun entityOutline(entity: Entity, vanillaOutline: Int): Int =
		guarded("entity glow", vanillaOutline) { it.glow(entity, vanillaOutline) }

	@JvmStatic
	fun entityRenderCancelled(entity: Entity): Boolean = guarded("entity render", false) { it.rendering(entity) }

	@JvmStatic
	fun bossBarCancelled(bossBar: BossEvent): Boolean = guarded("boss bar", false) { it.bossBar(bossBar) }

	internal class Channels(bus: EventBus) {
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
