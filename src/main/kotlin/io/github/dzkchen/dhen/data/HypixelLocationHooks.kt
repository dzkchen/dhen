package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.event.AreaChangeEvent
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuardedHooks
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.IslandChangeEvent
import io.github.dzkchen.dhen.event.TablistUpdateEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.guarded
import io.github.dzkchen.dhen.util.Failsafe

internal object HypixelLocationHooks : GuardedHooks<HypixelLocationHooks.Channels> {
	override val feed = "Location"

	private const val SKYBLOCK_OBJECTIVE = "SBScoreboard"
	private const val AREA_PREFIX = "Area: "
	private const val DUNGEON_PREFIX = "Dungeon: "
	override val failsafe = Failsafe("Dhen {} failed, its island events are off until restart")

	private var channels: Channels? = null

	private var subscriptions: Array<Handle> = emptyArray()
	private var fallback = false

	val islandChanges: Int get() = channels?.islandChanges ?: 0

	val areaChanges: Int get() = channels?.areaChanges ?: 0

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		subscriptions = arrayOf(
			bus.subscribe<WorldChangeEvent> { if (it.phase == WorldChange.DISCONNECT) disconnected() },
			bus.subscribe<TablistUpdateEvent>(BEFORE_FEATURES) { fallbackUpdated() }
		)
	}

	override fun uninstall() {
		subscriptions.forEach(Handle::unsubscribe)
		subscriptions = emptyArray()
		fallback = false
		channels = null
		SkyBlockLocation.reset()
	}

	override fun bound() = channels

	fun greeted() {
		guarded("Hypixel hello") { it.greeted() }
		fallbackUpdated()
	}

	val fallbackActive: Boolean get() = fallback

	fun modApiSupported() {
		fallback = false
	}

	fun modApiRefused() {
		fallback = true
		fallbackUpdated()
	}

	fun located(serverName: String, skyBlock: Boolean, mode: String?, map: String?) =
		guarded("Hypixel location") { it.located(serverName, skyBlock, mode, map) }

	fun scoreboardTitled(objective: String, title: String) {
		if (objective == SKYBLOCK_OBJECTIVE && SkyBlockLocation.awaitingGuestTitle) {
			guarded("Hypixel scoreboard title") { it.titled(title) }
		}
		fallbackUpdated()
	}

	private fun disconnected() = guarded("Hypixel disconnect") { it.disconnected() }

	private fun fallbackUpdated() {
		if (!fallback || !SkyBlockLocation.onHypixel) return
		guarded("location fallback") { it.fallback() }
	}

	internal class Channels(bus: EventBus) {
		private val islands = bus.type<IslandChangeEvent>()
		private val areas = bus.type<AreaChangeEvent>()

		var islandChanges: Int = 0
			private set

		var areaChanges: Int = 0
			private set

		fun greeted() = SkyBlockLocation.greeted()

		fun located(
			serverName: String?,
			skyBlock: Boolean,
			mode: String?,
			map: String?,
			resolvedIsland: Island? = mode?.let(Island::ofMode)
		) = publishing {
			SkyBlockLocation.located(serverName, skyBlock, mode, map, resolvedIsland)
			if (ScoreboardState.objective == SKYBLOCK_OBJECTIVE) SkyBlockLocation.titled(ScoreboardState.strippedTitle)
		}

		fun fallback() {
			if (ScoreboardState.objective != SKYBLOCK_OBJECTIVE) {
				located(null, false, null, null, Island.NONE)
				return
			}
			var resolved = Island.UNKNOWN
			var area: String? = null
			val lines = TablistState.stripped
			val islands = Island.entries
			var lineIndex = 0
			while (lineIndex < lines.size) {
				val line = lines[lineIndex]
				var prefixStart = 0
				while (prefixStart < line.length && line[prefixStart].isWhitespace()) prefixStart++
				val start = when {
					line.startsWith(AREA_PREFIX, prefixStart) -> prefixStart + AREA_PREFIX.length
					line.startsWith(DUNGEON_PREFIX, prefixStart) -> prefixStart + DUNGEON_PREFIX.length
					else -> {
						lineIndex++
						continue
					}
				}
				var islandIndex = 0
				while (islandIndex < islands.size) {
					val island = islands[islandIndex]
					val displayName = island.displayName
					if (displayName != null && line.length == start + displayName.length &&
						line.regionMatches(start, displayName, 0, displayName.length, ignoreCase = true)
					) {
						resolved = island
						area = displayName
						break
					}
					islandIndex++
				}
				break
			}
			located(null, true, null, area, resolved)
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
