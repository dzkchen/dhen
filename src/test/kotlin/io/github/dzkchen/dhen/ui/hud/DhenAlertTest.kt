package io.github.dzkchen.dhen.ui.hud

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DhenAlertTest {
	@Test
	fun `a second alert replaces the content and lifetime`() {
		val element = AlertHudElement()
		element.show(Component.literal("First"), Component.empty(), 20)
		element.tick()
		val replacement = Component.literal("Second").withStyle(ChatFormatting.RED)

		element.show(replacement, Component.literal("Subtitle"), 40)

		assertEquals("Second", element.currentTitle.string)
		assertEquals(replacement.style, element.currentTitle.style)
		assertEquals(40, element.ticksLeft)
	}

	@Test
	fun `the alert expires after exactly its client ticks`() {
		val element = AlertHudElement()
		element.show(Component.literal("Brief"), Component.empty(), 2)

		element.tick()
		assertTrue(element.hasContent)
		element.tick()
		assertFalse(element.hasContent)
		element.tick()
		assertEquals(0, element.ticksLeft)
	}

	@Test
	fun `editor preview is temporary and does not extend live content`() {
		val element = AlertHudElement()
		element.previewing(true)
		assertTrue(element.hasContent)
		assertEquals("Dhen Alert", element.currentTitle.string)

		element.show(Component.literal("Live"), Component.empty(), 1)
		element.tick()
		assertTrue(element.hasContent)
		element.previewing(false)
		assertFalse(element.hasContent)
	}

	@Test
	fun `default placement follows Noamm's proportional title offset`() {
		val element = AlertHudElement()

		assertEquals(213, element.placeY(480, 80))
		assertEquals(0, element.offsetYFor(HudAnchor.MIDDLE_CENTER, 480, 80, 213))
	}

	@Test
	fun `a non-positive lifetime cannot replace the current alert`() {
		val element = AlertHudElement()
		element.show(Component.literal("Kept"), Component.empty(), 10)

		assertThrows(IllegalArgumentException::class.java) {
			element.show(Component.literal("Rejected"), Component.empty(), 0)
		}
		assertEquals("Kept", element.currentTitle.string)
		assertEquals(10, element.ticksLeft)
	}
}
