package io.github.dzkchen.dhen.features.visual

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompactTabModelTest {
	@Test
	fun `advert hiding preserves ordinary header and footer text`() {
		assertEquals(listOf("Stats", "HYPIXEL.NET", "Cookie Buff"), CompactTabModel.frame("Stats\nHYPIXEL.NET\nCookie Buff", false))
		assertEquals(listOf("Stats", "Cookie Buff"), CompactTabModel.frame("Stats\nHYPIXEL.NET\nCookie Buff", true))
	}

	@Test
	fun `equal source titles merge and fire sale content is removed before layout`() {
		val lines = MutableList(40) { "" }
		lines[0] = "Info"
		lines[1] = "Location"
		lines[2] = " Village"
		lines[20] = "Info"
		lines[21] = "Fire Sales: (1)"
		lines[22] = " Buy a skin"
		lines[24] = "Skills"
		lines[25] = " Mining 60"
		val rows = CompactTabModel.columns(lines, "", true).flatten()
		assertEquals(1, rows.count { it.title && it.text == "Info" })
		assertFalse(rows.any { it.text.contains("skin") || it.text.contains("Fire Sales") })
		assertTrue(rows.any { it.text == " Mining 60" })
	}

	@Test
	fun `toggle rising edges ignore screens and reset on departure`() {
		val state = TabToggle()
		assertTrue(state.update(down = true, screen = false, toggle = true))
		assertTrue(state.update(down = true, screen = false, toggle = true))
		assertFalse(state.update(down = false, screen = true, toggle = true))
		assertFalse(state.update(down = true, screen = true, toggle = true))
		assertTrue(state.update(down = true, screen = false, toggle = true))
		state.reset()
		assertFalse(state.update(down = false, screen = false, toggle = true))
		assertTrue(state.update(down = true, screen = false, toggle = false))
		assertFalse(state.update(down = false, screen = false, toggle = false))
	}

	@Test
	fun `player parser keeps rank and suffix but excludes footer skyblock level`() {
		val player = CompactTabPlayer.read("§8[§b1,234§8] §6[MVP++] Player_Name §7♲ §c⚒", true) { -1 }!!
		assertEquals("Player_Name", player.name)
		assertEquals(1234, player.level)
		assertTrue(player.ironman)
		assertEquals("§c⚒", player.faction)
		assertFalse(player.suffix.contains("⚒"))
		assertNull(CompactTabPlayer.read("SB Level [123] Details", false) { -1 })
	}
}
