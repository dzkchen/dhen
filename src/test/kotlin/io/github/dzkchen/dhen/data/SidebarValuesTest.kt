package io.github.dzkchen.dhen.data

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SidebarValuesTest {
	@AfterEach
	fun clear() = SidebarValues.reset()

	@Test
	fun `the SkyBlock season line is the date, and carries no server code`() {
		SidebarValues.read(listOf(" Early Spring 13th"))

		assertEquals("Early Spring 13th", SidebarValues.date)
	}

	@Test
	fun `the real-world date and server code line is claimed but read by nobody`() {
		SidebarValues.read(listOf("§711/15/24 §8m10DH", " Late Summer 1st"))

		assertEquals("Late Summer 1st", SidebarValues.date)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the time line keeps its weather symbol`() {
		SidebarValues.read(listOf(" §75:50am §b☽", " §78:50am"))

		assertEquals("§75:50am §b☽", SidebarValues.time)
	}

	@Test
	fun `purse and bits keep the sidebar text and a comparable number`() {
		SidebarValues.read(listOf(" Purse: §6423,085,776", " Bits: §b140,965"))

		assertEquals("423,085,776", SidebarValues.purseText)
		assertEquals(423_085_776L, SidebarValues.purse)
		assertEquals("140,965", SidebarValues.bitsText)
		assertEquals(140_965L, SidebarValues.bits)
	}

	@Test
	fun `a piggy purse reads the same as a purse`() {
		SidebarValues.read(listOf(" Piggy: §61,000"))

		assertEquals(1_000L, SidebarValues.purse)
	}

	@Test
	fun `the location line is taken whole, and its area alone is offered beside it`() {
		SidebarValues.read(listOf(" §7⏣ §bVillage"))

		assertEquals("§7⏣ §bVillage", SidebarValues.location)
		assertEquals("Village", SidebarValues.area)
	}

	@Test
	fun `a slayer sub-line cannot be mistaken for the location line`() {
		SidebarValues.read(listOf(" Slayer Quest", " §7- §cVoidgloom Seraph III", " §7- §e12 Kills", " §7⏣ §bVillage"))

		assertEquals("Village", SidebarValues.area)
	}

	@Test
	fun `the slayer quest claims its header and the two lines under it`() {
		SidebarValues.read(
			listOf(
				" Slayer Quest",
				" §7- §cVoidgloom Seraph III",
				" §7- §e12§7/§c120 §7Kills",
				" §7⏣ §bVillage"
			)
		)

		assertEquals(
			listOf("Slayer Quest", "§7- §cVoidgloom Seraph III", "§7- §e12§7/§c120 §7Kills"),
			SidebarValues.slayer
		)
		assertEquals("§7⏣ §bVillage", SidebarValues.location)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `lines nothing recognised become the unknown block, short ones dropped`() {
		SidebarValues.read(listOf(" Purse: §6100", " §aPowder: §b1,000", "  ", " §e☘"))

		assertEquals(listOf("§aPowder: §b1,000"), SidebarValues.unknown)
	}

	@Test
	fun `a second read replaces the previous values`() {
		SidebarValues.read(listOf(" Purse: §6100", " §ewww.hypixel.net"))
		SidebarValues.read(listOf(" §aPowder: §b1,000"))

		assertEquals(SidebarValues.ABSENT, SidebarValues.purse)
		assertNull(SidebarValues.footer)
		assertEquals(listOf("§aPowder: §b1,000"), SidebarValues.unknown)
	}
}
