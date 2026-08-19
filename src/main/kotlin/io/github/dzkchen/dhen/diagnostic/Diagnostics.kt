package io.github.dzkchen.dhen.diagnostic

import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.data.HypixelLocationHooks
import io.github.dzkchen.dhen.data.ScoreboardHooks
import io.github.dzkchen.dhen.data.ScoreboardState
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetHooks
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.data.TablistHooks
import io.github.dzkchen.dhen.data.TablistState
import io.github.dzkchen.dhen.data.party.PartyHooks
import io.github.dzkchen.dhen.data.party.PartyState
import io.github.dzkchen.dhen.data.stats.ActionBarSegment
import io.github.dzkchen.dhen.data.stats.PlayerStats
import io.github.dzkchen.dhen.data.stats.PlayerStatsHooks
import io.github.dzkchen.dhen.event.TickHooks
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.ServerClock
import java.util.Locale

class Diagnostics(private val manager: ModuleManager) {
	var deepMode: Boolean
		get() = manager.profiler.deepMode
		set(value) {
			manager.profiler.deepMode = value
		}

	fun partyLines(): List<String> = buildList {
		if (!PartyHooks.active()) {
			add("Party: no feed, the party hooks are not installed")
			return@buildList
		}
		add(
			"Party: inParty=${PartyState.inParty}, leader=${PartyState.leader ?: "none"}, " +
				"members=${PartyState.members.size}, you=${PartyState.self ?: "unknown"}, " +
				"youLead=${PartyState.isLeader}, " +
				"packet=${if (PartyHooks.requesting) "available" else "refused"}"
		)
		for (member in PartyState.members) {
			add("  $member: role=${PartyState.roles[member] ?: "unconfirmed"}")
		}
	}

	fun scoreboardLines(): List<String> = buildList {
		if (!ScoreboardHooks.active()) {
			add("Scoreboard: no feed, the scoreboard hooks are not installed")
		} else {
			add("Scoreboard title: '${ScoreboardState.strippedTitle}' (objective ${ScoreboardState.objective.ifEmpty { "none" }})")
			for (line in ScoreboardState.stripped) add("  $line")
		}
		if (!TablistHooks.active()) {
			add("Tab list: no feed, the tab list hooks are not installed")
			return@buildList
		}
		add("Tab list header: '${TablistState.strippedHeader}'")
		add("Tab list footer: '${TablistState.strippedFooter}'")
		for (line in TablistState.stripped) add("  $line")
	}

	fun tablistWidgetLines(): List<String> = buildList {
		if (!TabWidgetHooks.active()) {
			add("Tab list widgets: no feed, the tab list widget hooks are not installed")
			return@buildList
		}
		val active = TabWidget.entries.filter(TabWidgetState::active)
		add("Tab list widgets: ${active.size} active of ${TabWidget.entries.size}")
		for (widget in active) {
			add("  ${widget.name}")
			for (line in TabWidgetState.stripped(widget)) add("    $line")
		}
	}

	fun statsLines(): List<String> = buildList {
		if (!PlayerStatsHooks.active()) {
			add("Player stats: no feed, the action bar hooks are not installed")
			return@buildList
		}
		add("Player stats: health=${PlayerStats.health}/${PlayerStats.maxHealth}, defense=${PlayerStats.defense}, ehp=${PlayerStats.effectiveHp}")
		add("  mana=${PlayerStats.mana}/${PlayerStats.maxMana}, overflow=${PlayerStats.overflowMana}, speed=${PlayerStats.speed}")
		add("  vitality=${PlayerStats.vitality}/${PlayerStats.maxVitality}, shown=${PlayerStats.vitalityShown}")
		add("  stacks=${PlayerStats.netherArmorStacks}${PlayerStats.stackSymbol}, salvation=${PlayerStats.salvation}, secrets=${PlayerStats.secrets}/${PlayerStats.maxSecrets}")
		add("  hidden from the action bar: ${hiddenSegments()}")
	}

	private fun hiddenSegments(): String =
		ActionBarSegment.entries.filter(PlayerStats::hidden).joinToString().ifEmpty { "nothing" }

	fun lines(): List<String> = buildList {
		add(
			"Dhen debug: deep profiling ${if (deepMode) "on" else "off"}, " +
				"modules.json v${ModulePersistence.version}"
		)
		add(
			if (!TickHooks.active()) "Server tick: no feed, the tick hooks are not installed"
			else "Server tick: tps=${String.format(Locale.ROOT, "%.1f", ServerClock.tps)}, " +
				"serverTicks=${ServerClock.ticks}, " +
				"clientTicksSincePing=${ServerClock.clientTicksSinceServerTick}"
		)
		add(
			if (!HypixelLocationHooks.active()) "Location: no feed, the Hypixel Mod API hooks are not installed"
			else "Location: hypixel=${SkyBlockLocation.onHypixel}, skyblock=${SkyBlockLocation.inSkyBlock}, " +
				"island=${SkyBlockLocation.island}, area=${SkyBlockLocation.area ?: "none"}, " +
				"mode=${SkyBlockLocation.mode ?: "none"}, server=${SkyBlockLocation.serverName ?: "none"}, " +
				"islandChanges=${HypixelLocationHooks.islandChanges}, " +
				"areaChanges=${HypixelLocationHooks.areaChanges}, " +
				"guest=${SkyBlockLocation.isGuest}, " +
				"awaitingGuestTitle=${SkyBlockLocation.awaitingGuestTitle}"
		)
		add(
			if (!ScoreboardHooks.active()) "Scoreboard: no feed, the scoreboard hooks are not installed"
			else "Scoreboard: title='${ScoreboardState.strippedTitle}', lines=${ScoreboardState.lines.size}, " +
				"scoreboardArea=${ScoreboardState.area ?: "none"}"
		)
		add(
			if (!TablistHooks.active()) "Tab list: no feed, the tab list hooks are not installed"
			else "Tab list: lines=${TablistState.lines.size}, header=${TablistState.strippedHeader.isNotEmpty()}, " +
				"footer=${TablistState.strippedFooter.isNotEmpty()}"
		)
		add(
			if (!TabWidgetHooks.active()) "Tab list widgets: no feed, the tab list widget hooks are not installed"
			else "Tab list widgets: active=${TabWidget.entries.count(TabWidgetState::active)} of ${TabWidget.entries.size}"
		)
		add(
			if (!PlayerStatsHooks.active()) "Player stats: no feed, the action bar hooks are not installed"
			else "Player stats: health=${PlayerStats.health}/${PlayerStats.maxHealth}, mana=${PlayerStats.mana}/${PlayerStats.maxMana}, " +
				"defense=${PlayerStats.defense}, speed=${PlayerStats.speed}"
		)
		for (module in manager.modules) {
			add(
				"${module.name}: subscriptions=${module.subscriptionCount}, " +
					"keybinds=${manager.keybindCount(module)}, hud=${module.hudElements.size}, " +
					"errors=${module.errorCount}"
			)
			for (timing in module.handlerTimings) {
				val snapshot = timing.snapshot()
				add(
					"  ${snapshot.eventName}: calls=${snapshot.totalInvocations}, " +
						"rollingAvg=${snapshot.averageNanos}ns, rollingMax=${snapshot.maxNanos}ns, " +
						"samples=${snapshot.sampleCount}"
				)
			}
		}
	}
}
