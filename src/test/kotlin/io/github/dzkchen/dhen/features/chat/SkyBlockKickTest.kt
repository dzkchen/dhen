package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.module.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SkyBlockKickTest {
	private lateinit var element: KickTimerElement

	@BeforeEach
	fun prepare() {
		element = KickTimerElement()
		SkyBlockKick.element.clear()
	}

	@Test
	fun `the module lands in Misc with one toggle and one movable element`() {
		assertEquals(Category.MISC, SkyBlockKick.category)
		assertEquals(listOf("Send Party Message"), SkyBlockKick.settings.map { it.name })
		assertEquals(listOf("SB Kick Timer"), SkyBlockKick.hudElements.map { it.name })
	}

	@Test
	fun `the timer counts up in hundredths from the moment of the kick`() {
		element.start(NOW)

		assertTrue(element.showing)
		assertEquals("§cLast kicked from SkyBlock §b0.00s ago", element.line)

		element.refresh(NOW + 1_500L, inSkyBlock = false)
		assertEquals("§cLast kicked from SkyBlock §b1.50s ago", element.line)

		element.refresh(NOW + 12_070L, inSkyBlock = false)
		assertEquals("§cLast kicked from SkyBlock §b12.07s ago", element.line)
	}

	@Test
	fun `the overlay hides itself a minute after the kick`() {
		element.start(NOW)

		element.refresh(NOW + 59_999L, inSkyBlock = false)
		assertTrue(element.showing)

		element.refresh(NOW + 60_000L, inSkyBlock = false)
		assertFalse(element.showing)
	}

	@Test
	fun `getting back into SkyBlock clears the overlay ten seconds after the kick`() {
		element.start(NOW)

		element.refresh(NOW + 10_000L, inSkyBlock = true)
		assertTrue(element.showing)

		element.refresh(NOW + 10_001L, inSkyBlock = true)
		assertFalse(element.showing)
	}

	@Test
	fun `the overlay forgets its last reading when it hides`() {
		element.start(NOW)
		element.refresh(NOW + 59_000L, inSkyBlock = false)
		element.refresh(NOW + 60_000L, inSkyBlock = false)

		assertFalse(element.showing)
		assertEquals("§cLast kicked from SkyBlock §b0.00s ago", element.line)
	}

	@Test
	fun `a second kick while the overlay is up does not restart the count`() {
		SkyBlockKick.kicked("There was a problem joining SkyBlock, try again in a moment!", NOW)
		SkyBlockKick.element.refresh(NOW + 5_000L, inSkyBlock = false)
		SkyBlockKick.kicked("You were kicked while joining that server!", NOW + 5_000L)
		SkyBlockKick.element.refresh(NOW + 5_000L, inSkyBlock = false)

		assertEquals("§cLast kicked from SkyBlock §b5.00s ago", SkyBlockKick.element.line)
	}

	@Test
	fun `an ordinary chat line never starts the timer`() {
		SkyBlockKick.kicked("You were kicked while joining that server", NOW)

		assertFalse(SkyBlockKick.element.showing)
	}

	private companion object {
		const val NOW = 1_000_000L
	}
}
