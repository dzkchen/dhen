package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.mixin.ClientTextTooltipAccessor
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.util.FormattedCharSequence
import org.joml.Vector2i
import org.joml.Vector2ic

private const val SCREEN_MARGIN = 6
private const val CURSOR_LIFT = 12

object TooltipShape {
	private val rewrapped = ArrayList<ClientTooltipComponent>()
	private val clamp = ClampedPositioner()

	@JvmStatic
	fun shaped(font: Font, lines: List<ClientTooltipComponent>, guiWidth: Int): List<ClientTooltipComponent> {
		if (!ItemTooltip.clamping()) return lines
		val room = guiWidth - 2 * SCREEN_MARGIN
		if (room <= 0 || !overflows(font, lines, room)) return lines
		rewrapped.clear()
		for (index in lines.indices) {
			val line = lines[index]
			val text = sequence(line)
			if (text == null || line.getWidth(font) <= room) {
				rewrapped += line
				continue
			}
			val pieces = DhenType.rewrap(font, text, room)
			if (pieces.isEmpty()) {
				rewrapped += ClientTooltipComponent.create(FormattedCharSequence.EMPTY)
				continue
			}
			for (piece in pieces) rewrapped += ClientTooltipComponent.create(piece)
		}
		return rewrapped
	}

	@JvmStatic
	fun placed(positioner: ClientTooltipPositioner): ClientTooltipPositioner {
		if (!ItemTooltip.clamping() || ItemTooltip.scrolling()) return positioner
		clamp.delegate = positioner
		return clamp
	}

	internal fun forget() {
		rewrapped.clear()
		rewrapped.trimToSize()
		clamp.forget()
	}

	private fun overflows(font: Font, lines: List<ClientTooltipComponent>, room: Int): Boolean {
		for (index in lines.indices) {
			val line = lines[index]
			if (sequence(line) != null && line.getWidth(font) > room) return true
		}
		return false
	}

	private fun sequence(line: ClientTooltipComponent): FormattedCharSequence? =
		(line as? ClientTextTooltipAccessor)?.tooltipText()
}

internal class ClampedPositioner : ClientTooltipPositioner {
	private var host: ClientTooltipPositioner? = null
	private val placed = Vector2i()

	var delegate: ClientTooltipPositioner
		get() = host!!
		set(value) {
			host = value
		}

	fun forget() {
		host = null
	}

	override fun positionTooltip(
		screenWidth: Int,
		screenHeight: Int,
		mouseX: Int,
		mouseY: Int,
		width: Int,
		height: Int
	): Vector2ic {
		val start = delegate.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height)
		val rightmost = maxOf(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN)
		var x = start.x().coerceIn(SCREEN_MARGIN, rightmost)
		var y = start.y().coerceIn(SCREEN_MARGIN, maxOf(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN))
		if (x == SCREEN_MARGIN && y != SCREEN_MARGIN && width + 2 * SCREEN_MARGIN <= screenWidth) {
			x = (mouseX - width / 2).coerceIn(SCREEN_MARGIN, rightmost)
			y = mouseY - height - CURSOR_LIFT
			if (y < SCREEN_MARGIN) y = if (screenHeight - mouseY > mouseY) mouseY + CURSOR_LIFT else SCREEN_MARGIN
		}
		return placed.set(x, y)
	}
}
