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
		val panelWidth = minOf(320, width - 32)
		val left = centerX - panelWidth / 2
		var y = 36
		val diagnostics = runtime.diagnostics()

		for (addon in diagnostics.addons) {
			val authors = if (addon.authors.isEmpty()) "unknown author" else addon.authors.joinToString(", ")
			val source = addon.sourceLocation ?: addon.sourceUrl ?: "unknown"
			addRenderableWidget(
				StringWidget(left, y, panelWidth, 20, Component.literal("${addon.name} v${addon.version} (${addon.artifactType})"), font),
			)
			y += 14
			addRenderableWidget(
				StringWidget(left, y, panelWidth, 20, Component.literal("$authors - API ${addon.requiredDhenApi} - ${addon.sourceType}: $source"), font),
			)
			y += 24
		}

		for (record in runtime.modules()) {
			val label = "${record.module.metadata.category.displayName}: ${record.module.metadata.name} - ${stateLabel(record.state)}"
			addRenderableWidget(
				Button.builder(Component.literal(label)) {
					runtime.toggleModule(record.id)
					rebuildWidgets()
				}.bounds(left, y, panelWidth, 20).build(),
			)
			y += 24
		}

		addRenderableWidget(
			StringWidget(left, y, panelWidth, 20, Component.literal("Active handles: ${diagnostics.totalActiveHandles}"), font),
		)
		y += 24

		addRenderableWidget(
			Button.builder(Component.literal("Done")) { onClose() }
				.bounds(left, y + 8, panelWidth, 20).build(),
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
