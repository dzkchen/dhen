package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.AreaChangeEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.util.Failsafe

internal object HypixelLocationHooks : GuardedHooks<HypixelLocationHooks.Channels> {
	override val feed = "Location"

	private const val SKYBLOCK_OBJECTIVE = "SBScoreboard"

	override val failsafe = Failsafe("Dhen {} failed, its island events are off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()

	val islandChanges: Int get() = channels?.islandChanges ?: 0

	val areaChanges: Int get() = channels?.areaChanges ?: 0

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		subscriptions = arrayOf(
			bus.subscribe<WorldChangeEvent> { if (it.phase == WorldChange.DISCONNECT) disconnected() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		channels = null
		SkyBlockLocation.reset()
	}

	override fun bound() = channels

	fun greeted() = guarded("Hypixel hello") { it.greeted() }

	fun located(serverName: String, skyBlock: Boolean, mode: String?, map: String?) =
		guarded("Hypixel location") { it.located(serverName, skyBlock, mode, map) }

	fun scoreboardTitled(objective: String, title: String) {
		if (objective != SKYBLOCK_OBJECTIVE || !SkyBlockLocation.awaitingGuestTitle) return
		guarded("Hypixel scoreboard title") { it.titled(title) }
	}

	private fun disconnected() = guarded("Hypixel disconnect") { it.disconnected() }

	internal class Channels(bus: EventBus) {
		private val islands = bus.type<IslandChangeEvent>()
		private val areas = bus.type<AreaChangeEvent>()

		var islandChanges: Int = 0
			private set

		var areaChanges: Int = 0
			private set

		fun greeted() = SkyBlockLocation.greeted()

		fun located(serverName: String, skyBlock: Boolean, mode: String?, map: String?) = publishing {
			SkyBlockLocation.located(serverName, skyBlock, mode, map)
			if (ScoreboardState.objective == SKYBLOCK_OBJECTIVE) SkyBlockLocation.titled(ScoreboardState.strippedTitle)
		}

		fun titled(title: String) = publishing { SkyBlockLocation.titled(title) }

		fun disconnected() = publishing(SkyBlockLocation::reset)

		private inline fun publishing(change: () -> Unit) {
			val previousIsland = SkyBlockLocation.island
			val previousArea = SkyBlockLocation.area
			change()
			if (SkyBlockLocation.island != previousIsland) {
				islandChanges++
				islands.dispatch(IslandChangeEvent(SkyBlockLocation.island, previousIsland))
			}
			if (SkyBlockLocation.area != previousArea) {
				areaChanges++
				areas.dispatch(AreaChangeEvent(SkyBlockLocation.area, previousArea))
			}
		}
	}
}
