package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.module.Module

internal class HudTarget(val module: Module, val element: HudElement) {
	var contentWidth: Int = 0
	var contentHeight: Int = 0
	var placeholder: Boolean = false
	var x: Int = 0
	var y: Int = 0
	var width: Int = 0
	var height: Int = 0

	val rendering: Boolean
		get() = module.enabled && element.isActive

	fun contains(px: Int, py: Int): Boolean =
		px >= x && px < x + width && py >= y && py < y + height
}
