package io.github.dzkchen.dhen.diagnostic

import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.module.ModuleManager

class Diagnostics(
	private val manager: ModuleManager,
	private val configVersion: () -> Int = { ModulePersistence.version }
) {
	var deepMode: Boolean
		get() = manager.eventBus.profiler.deepMode
		set(value) {
			manager.eventBus.profiler.deepMode = value
		}

	fun lines(): List<String> = buildList {
		add("Dhen debug: deep profiling ${if (deepMode) "on" else "off"}")
		for (module in manager.modules) {
			add(
				"${module.name}: subscriptions=${module.subscriptionCount}, " +
					"keybinds=${manager.keybindCount(module)}, errors=${module.errorCount}, " +
					"config=modules:v${configVersion()}"
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
