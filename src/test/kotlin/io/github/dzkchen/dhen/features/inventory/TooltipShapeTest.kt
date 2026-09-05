package io.github.dzkchen.dhen.features.inventory

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import org.joml.Vector2i
import org.joml.Vector2ic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TooltipShapeTest {
	@Test
	fun `a tooltip past the right or bottom edge is pulled back inside the margin`() {
		val placed = clamped(at(500, 460), screenWidth = 640, screenHeight = 480, width = 200, height = 60)

		assertEquals(640 - 200 - 6, placed.x())
		assertEquals(480 - 60 - 6, placed.y())
	}

	@Test
	fun `a tooltip pinned to the left edge is re-centred over the cursor and lifted above it`() {
		val placed = clamped(at(-40, 300), screenWidth = 640, screenHeight = 480, width = 200, height = 60, mouseX = 320, mouseY = 300)

		assertEquals(320 - 100, placed.x())
		assertEquals(300 - 60 - 12, placed.y())
	}

	@Test
	fun `a lift that would leave the top drops the tooltip below the cursor when there is room there`() {
		val placed = clamped(at(-40, 300), screenWidth = 640, screenHeight = 480, width = 200, height = 60, mouseX = 320, mouseY = 40)

		assertEquals(40 + 12, placed.y())
	}

	@Test
	fun `a tooltip wider than the screen sits on the left margin and keeps its row`() {
		val placed = clamped(at(-40, 300), screenWidth = 300, screenHeight = 480, width = 400, height = 60, mouseX = 150, mouseY = 300)

		assertEquals(6, placed.x())
		assertEquals(300, placed.y())
	}

	private fun at(x: Int, y: Int): ClientTooltipPositioner =
		ClientTooltipPositioner { _, _, _, _, _, _ -> Vector2i(x, y) }

	private fun clamped(
		positioner: ClientTooltipPositioner,
		screenWidth: Int,
		screenHeight: Int,
		width: Int,
		height: Int,
		mouseX: Int = 0,
		mouseY: Int = 0
	): Vector2ic {
		val clamp = ClampedPositioner()
		clamp.delegate = positioner
		return clamp.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height)
	}
}
