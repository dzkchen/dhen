package io.github.dzkchen.dhen.diagnostic

import io.github.dzkchen.dhen.config.ModulePersistence
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
