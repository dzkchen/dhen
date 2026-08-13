package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.module.ModuleManager
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun interface HudMetrics {
	fun measure(target: HudTarget)
}

internal class HudEditor(manager: ModuleManager, private val metrics: HudMetrics) {
	val targets: List<HudTarget> = targetsOf(manager)

	var selected: HudTarget? = null
		private set

	private val horizontalSnap = SnapSolver()
	private val verticalSnap = SnapSolver()
	private var dragged: HudTarget? = null
	private var grabX = 0
	private var grabY = 0
	private var pressedOffsetX = 0
	private var pressedOffsetY = 0
	private var scrolled = 0.0
	private var screenWidth = 0
	private var screenHeight = 0

	val dragging: Boolean
		get() = dragged != null
	val guideX: Int
		get() = horizontalSnap.line
	val guideY: Int
		get() = verticalSnap.line

	fun layout(screenWidth: Int, screenHeight: Int) {
		this.screenWidth = screenWidth
		this.screenHeight = screenHeight
		for (i in targets.indices) {
			val target = targets[i]
			metrics.measure(target)
			val scale = target.element.scale
			target.width = HudLayout.scaled(target.contentWidth, scale)
			target.height = HudLayout.scaled(target.contentHeight, scale)
			target.x = HudLayout.placeOnScreen(target.element.anchor.horizontal, screenWidth, target.width, target.element.offsetX)
			target.y = HudLayout.placeOnScreen(target.element.anchor.vertical, screenHeight, target.height, target.element.offsetY)
		}
	}

	fun targetAt(x: Int, y: Int): HudTarget? {
		for (i in targets.size - 1 downTo 0) {
			val target = targets[i]
			if (target.contains(x, y)) return target
		}
		return null
	}

	fun press(x: Int, y: Int): Boolean {
		clearGuides()
		val target = targetAt(x, y)
		selected = target
		if (target == null) return false
		dragged = target
		grabX = x - target.x
		grabY = y - target.y
		pressedOffsetX = target.element.offsetX
		pressedOffsetY = target.element.offsetY
		return true
	}

	fun drag(x: Int, y: Int, snap: Boolean) {
		val target = dragged ?: return
		moveTo(target, x - grabX, y - grabY, snap)
	}

	fun release(): Boolean {
		val target = dragged
		dragged = null
		clearGuides()
		if (target == null) return false
		val moved = target.element.offsetX != pressedOffsetX || target.element.offsetY != pressedOffsetY
		if (moved) reanchor(target)
		return moved
	}

	fun nudge(dx: Int, dy: Int): Boolean {
		val target = selected ?: return false
		return moveTo(target, target.x + dx, target.y + dy, snap = false)
	}

	fun reset(x: Int, y: Int): Boolean {
		if (dragging) return false
		val target = targetAt(x, y) ?: return false
		selected = target
		return target.element.resetToDeclared()
	}

	fun rescale(x: Int, y: Int, scroll: Double): Boolean {
		if (dragging) return false
		scrolled += scroll
		val steps = scrolled.toInt()
		scrolled -= steps
		if (steps == 0) return false
		val target = targetAt(x, y) ?: selected ?: return false
		selected = target
		val element = target.element
		val before = element.scale
		element.scale = quantize(before + steps * SCALE_STEP)
		return element.scale != before
	}

	private fun moveTo(target: HudTarget, px: Int, py: Int, snap: Boolean): Boolean {
		var x = HudLayout.clamp(px, target.width, screenWidth)
		var y = HudLayout.clamp(py, target.height, screenHeight)
		if (snap) {
			x = HudLayout.clamp(
				snapAxis(horizontalSnap, target, x, target.width, screenWidth, true),
				target.width,
				screenWidth
			)
			y = HudLayout.clamp(
				snapAxis(verticalSnap, target, y, target.height, screenHeight, false),
				target.height,
				screenHeight
			)
		} else {
			clearGuides()
		}
		val offsetX = HudLayout.offsetFor(target.element.anchor.horizontal, screenWidth, target.width, x)
		val offsetY = HudLayout.offsetFor(target.element.anchor.vertical, screenHeight, target.height, y)
		if (offsetX == target.element.offsetX && offsetY == target.element.offsetY) return false
		target.element.offsetX = offsetX
		target.element.offsetY = offsetY
		target.x = x
		target.y = y
		return true
	}

	private fun snapAxis(
		solver: SnapSolver,
		moving: HudTarget,
		position: Int,
		size: Int,
		screen: Int,
		horizontal: Boolean
	): Int {
		solver.reset(position)
		for (edge in 0..EDGES) {
			val own = position + size * edge / EDGES
			solver.consider(own, screen * edge / EDGES)
			for (i in targets.indices) {
				val other = targets[i]
				if (other === moving) continue
				val base = if (horizontal) other.x else other.y
				val extent = if (horizontal) other.width else other.height
				for (otherEdge in 0..EDGES) solver.consider(own, base + extent * otherEdge / EDGES)
			}
		}
		return solver.position
	}

	private fun reanchor(target: HudTarget) {
		val anchors = HudAnchor.entries
		var best = target.element.anchor
		var bestCost = anchorCost(best, target)
		for (i in anchors.indices) {
			val anchor = anchors[i]
			val cost = anchorCost(anchor, target)
			if (cost >= bestCost) continue
			bestCost = cost
			best = anchor
		}
		if (best == target.element.anchor) return
		target.element.anchor = best
		target.element.offsetX = HudLayout.offsetFor(best.horizontal, screenWidth, target.width, target.x)
		target.element.offsetY = HudLayout.offsetFor(best.vertical, screenHeight, target.height, target.y)
	}

	private fun anchorCost(anchor: HudAnchor, target: HudTarget): Int =
		abs(HudLayout.offsetFor(anchor.horizontal, screenWidth, target.width, target.x)) +
			abs(HudLayout.offsetFor(anchor.vertical, screenHeight, target.height, target.y))

	private fun clearGuides() {
		horizontalSnap.clear()
		verticalSnap.clear()
	}

	private fun quantize(scale: Float): Float = (scale * SCALE_PRECISION).roundToInt() / SCALE_PRECISION

	private class SnapSolver {
		private var origin = 0
		private var distance = SNAP_LIMIT

		var position = 0
			private set
		var line = NO_GUIDE
			private set

		fun reset(position: Int) {
			origin = position
			this.position = position
			distance = SNAP_LIMIT
			line = NO_GUIDE
		}

		fun clear() {
			line = NO_GUIDE
		}

		fun consider(own: Int, candidate: Int) {
			val delta = abs(candidate - own)
			if (delta >= distance) return
			distance = delta
			position = origin + (candidate - own)
			line = candidate
		}
	}

	companion object {
		const val NO_GUIDE = Int.MIN_VALUE

		private const val SCALE_STEP = 0.1f
		private const val SCALE_PRECISION = 100.0f
		private const val SNAP_LIMIT = 5
		private const val EDGES = 2

		private fun targetsOf(manager: ModuleManager): List<HudTarget> {
			val targets = mutableListOf<HudTarget>()
			for (module in manager.ordered) {
				for (element in module.hudElements) targets += HudTarget(module, element)
			}
			return targets
		}
	}
}
