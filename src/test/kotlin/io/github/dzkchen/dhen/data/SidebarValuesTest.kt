package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.util.NO_DIGITS
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

		assertEquals("Early Spring 13th", SidebarValues.text(SidebarField.DATE))
	}

	@Test
	fun `the real-world date and server code line keeps its date half`() {
		SidebarValues.read(listOf("§711/15/24 §8m10DH", " Late Summer 1st"))

		assertEquals("11/15/24", SidebarValues.text(SidebarField.LOBBY_CODE))
		assertEquals("Late Summer 1st", SidebarValues.text(SidebarField.DATE))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the time line keeps its weather symbol`() {
		SidebarValues.read(listOf(" §75:50am §b☽", " §78:50am"))

		assertEquals("§75:50am §b☽", SidebarValues.text(SidebarField.TIME))
	}

	@Test
	fun `purse and bits keep the sidebar text and a comparable number`() {
		SidebarValues.read(listOf(" Purse: §6423,085,776", " Bits: §b140,965"))

		assertEquals("423,085,776", SidebarValues.text(SidebarField.PURSE))
		assertEquals(423_085_776L, SidebarValues.number(SidebarField.PURSE))
		assertEquals("140,965", SidebarValues.text(SidebarField.BITS))
		assertEquals(140_965L, SidebarValues.number(SidebarField.BITS))
	}

	@Test
	fun `a piggy purse reads the same as a purse`() {
		SidebarValues.read(listOf(" Piggy: §61,000"))

		assertEquals(1_000L, SidebarValues.number(SidebarField.PURSE))
	}

	@Test
	fun `a fractional purse counts whole coins rather than folding in its decimal`() {
		SidebarValues.read(listOf(" Purse: §61,234.5"))

		assertEquals("1,234.5", SidebarValues.text(SidebarField.PURSE))
		assertEquals(1_234L, SidebarValues.number(SidebarField.PURSE))
	}

	@Test
	fun `the location line is taken whole, and its area alone is offered beside it`() {
		SidebarValues.read(listOf(" §7⏣ §bVillage"))

		assertEquals("§7⏣ §bVillage", SidebarValues.text(SidebarField.LOCATION))
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
		assertEquals("§7⏣ §bVillage", SidebarValues.text(SidebarField.LOCATION))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `lines nothing recognised become the unknown block, short ones dropped`() {
		SidebarValues.read(listOf(" Purse: §6100", " §aBogus: §b1,000", "  ", " §e☘"))

		assertEquals(listOf("§aBogus: §b1,000"), SidebarValues.unknown)
	}

	@Test
	fun `a second read replaces the previous values`() {
		SidebarValues.read(listOf(" Purse: §6100", " §ewww.hypixel.net"))
		SidebarValues.read(listOf(" §aBogus: §b1,000"))

		assertEquals(NO_DIGITS, SidebarValues.number(SidebarField.PURSE))
		assertNull(SidebarValues.text(SidebarField.FOOTER))
		assertEquals(listOf("§aBogus: §b1,000"), SidebarValues.unknown)
	}

	@Test
	fun `the long-tail currency lines are claimed and keep their grouped text`() {
		SidebarValues.read(
			listOf(
				" Motes: §5137,242",
				" Copper: §c3,416",
				" Sowdust: §230,120,093",
				" North Stars: §d1,539",
				" Gems: §a350"
			)
		)

		assertEquals("137,242", SidebarValues.text(SidebarField.MOTES))
		assertEquals("3,416", SidebarValues.text(SidebarField.COPPER))
		assertEquals("30,120,093", SidebarValues.text(SidebarField.SOWDUST))
		assertEquals("1,539", SidebarValues.text(SidebarField.NORTH_STARS))
		assertEquals("350", SidebarValues.text(SidebarField.GEMS))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `a sowdust line showing a gain is claimed but read from the tab list instead`() {
		SidebarValues.read(listOf(" Sowdust: §26.5k §7(+912)"))

		assertNull(SidebarValues.text(SidebarField.SOWDUST))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `heat keeps its own colour and cold is stored without its minus sign`() {
		SidebarValues.read(listOf(" Heat: §c14♨", " Cold: §b-3❄"))

		assertEquals("§c14♨", SidebarValues.text(SidebarField.HEAT))
		assertEquals(3L, SidebarValues.number(SidebarField.COLD))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `an immune heat line keeps the word rather than a number`() {
		SidebarValues.read(listOf(" Heat: §6IMMUNE"))

		assertEquals("§6IMMUNE", SidebarValues.text(SidebarField.HEAT))
	}

	@Test
	fun `the visiting line is kept whole and its maximum read out of it`() {
		SidebarValues.read(listOf(" §a✌ §7(§a11§7/20§7)"))

		assertEquals("§a✌ §7(§a11§7/20§7)", SidebarValues.text(SidebarField.VISITING))
		assertEquals(20L, SidebarValues.maxVisitors)
	}

	@Test
	fun `every powder kind on the sidebar is claimed under its own name`() {
		SidebarValues.read(
			listOf(
				" §2᠅ §fMithril§f: §235,448",
				" §d᠅ §fGemstone Powder§f: §d36,758",
				" §b᠅ §fGlacite§f: §b1,204"
			)
		)

		assertEquals("35,448", SidebarValues.powder(PowderKind.MITHRIL))
		assertEquals("36,758", SidebarValues.powder(PowderKind.GEMSTONE))
		assertEquals("1,204", SidebarValues.powder(PowderKind.GLACITE))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `a powder line is not mistaken for the location line`() {
		SidebarValues.read(listOf(" §2᠅ §fMithril§f: §235,448", " §7⏣ §bVillage"))

		assertEquals("§7⏣ §bVillage", SidebarValues.text(SidebarField.LOCATION))
	}

	@Test
	fun `the objective block takes its header, the line under it and matching continuations`() {
		SidebarValues.read(
			listOf(
				" Objective",
				" §eProtect Elle §7(§a98%§7)",
				" §7(§e1§7/§a100§7)",
				" §7⏣ §bVillage"
			)
		)

		assertEquals(
			listOf("Objective", "§eProtect Elle §7(§a98%§7)", "§7(§e1§7/§a100§7)"),
			SidebarValues.objective
		)
		assertEquals("§7⏣ §bVillage", SidebarValues.text(SidebarField.LOCATION))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the objective block stops at the first line that is not a continuation`() {
		SidebarValues.read(listOf(" Objective", " §eStar Dhen on Github", " §ewww.hypixel.net"))

		assertEquals(listOf("Objective", "§eStar Dhen on Github"), SidebarValues.objective)
		assertEquals("§ewww.hypixel.net", SidebarValues.text(SidebarField.FOOTER))
	}

	@Test
	fun `a stray quest line is claimed without joining the objective block`() {
		SidebarValues.read(listOf(" Objective", " §eStar Dhen on Github", " §eKill 100 Automatons"))

		assertEquals(listOf("Objective", "§eStar Dhen on Github"), SidebarValues.objective)
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}

	@Test
	fun `the profile marker line is claimed so it never reads as undetected`() {
		SidebarValues.read(listOf(" §7♲ §7Ironman"))

		assertEquals("§7♲ §7Ironman", SidebarValues.text(SidebarField.PROFILE_TYPE))
		assertEquals(emptyList<String>(), SidebarValues.unknown)
	}
}
