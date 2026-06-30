package io.github.dzkchen.dhen.platform

import io.github.dzkchen.dhen.runtime.DhenRuntime
import io.github.dzkchen.dhen.runtime.LifecycleState
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DhenScreen(private val parent: Screen, private val runtime: DhenRuntime) : Screen(Component.literal("Dhen")) {
	override fun init() {
		val centerX = width / 2
		var y = 36

		for (record in runtime.modules()) {
			val label = "${record.module.metadata.name}: ${stateLabel(record.state)}"
			addRenderableWidget(
				Button.builder(Component.literal(label)) {
					runtime.toggleModule(record.id)
					rebuildWidgets()
				}.bounds(centerX - 120, y, 240, 20).build(),
			)
			y += 24
		}

		val diagnostics = runtime.diagnostics()
		addRenderableWidget(
			StringWidget(centerX - 120, y, 240, 20, Component.literal("Active handles: ${diagnostics.totalActiveHandles}"), font),
		)
		y += 24

		addRenderableWidget(
			Button.builder(Component.literal("Done")) { onClose() }
				.bounds(centerX - 120, y + 8, 240, 20).build(),
		)
	}

	override fun onClose() {
		minecraft.setScreenAndShow(parent)
	}

	private fun stateLabel(state: LifecycleState): String = when (state) {
		LifecycleState.ENABLED -> "ON"
		LifecycleState.FAILED -> "FAILED"
		else -> "OFF"
	}
}
