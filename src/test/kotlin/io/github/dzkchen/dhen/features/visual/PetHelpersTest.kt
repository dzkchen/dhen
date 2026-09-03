package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.pet.PetLines
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PetHelpersTest {
	private val tooltip = PetExpTooltip()
	private val nametags = PetNametags()

	@Test
	fun `pet experience tooltip uses the source thresholds and level markers`() {
		val bingo = pet("BINGO", "COMMON", 2_812_392.5)
		val dragon = pet("GOLDEN_DRAGON", "LEGENDARY", 25_353_229.0)

		assertEquals("Progress to Level 100: 50.00%", tooltip.lines(bingo, dragonEgg = true, maxLevel = 100)[0].string)
		assertTrue(tooltip.lines(dragon, dragonEgg = true, maxLevel = 200)[0].string.startsWith("Progress to Level 100:"))
		assertTrue(tooltip.lines(dragon, dragonEgg = false, maxLevel = 200)[0].string.startsWith("Progress to Level 200:"))
		assertTrue(tooltip.lines(pet("GOLDEN_DRAGON", "LEGENDARY", 210_255_385.0), false, 200).isEmpty())
	}

	@Test
	fun `pet experience lines are inserted after either vanilla progress marker`() {
		assertEquals(3, tooltip.insertionIndex(listOf(Component.literal("Name"), Component.literal("MAX LEVEL"))))
		assertEquals(4, tooltip.insertionIndex(listOf(Component.literal("Name"), Component.literal("Progress to Level 83"))))
		assertEquals(-1, tooltip.insertionIndex(listOf(Component.literal("Name"))))
	}

	@Test
	fun `George chooses the cheapest allowed rarity and totals only priced pets`() {
		val helper = GeorgeHelper()
		val prices = mapOf(
			"PET-BLACK_CAT-LEGENDARY" to 12_000_000.0,
			"PET-BLACK_CAT-EPIC" to 8_000_000.0,
			"PET-JELLYFISH-EPIC" to 4_000_000.0
		)
		val lore = listOf(
			Component.literal("  Legendary Black Cat"),
			Component.literal("  Epic Jellyfish"),
			Component.literal("Not a wanted pet")
		)

		val requested = helper.buildLines(lore, false, prices::get)
		val otherTiers = helper.buildLines(lore, true, prices::get)

		assertTrue(requested[1].contains("Legendary Black Cat") && requested[1].contains("12,000,000"))
		assertTrue(otherTiers[1].contains("Epic Black Cat") && otherTiers[1].contains("8,000,000"))
		assertEquals("§7Total Cost: §612,000,000 coins", otherTiers.last())
	}

	@Test
	fun `an unavailable adjacent rarity never beats a real price`() {
		val lines = GeorgeHelper().buildLines(
			listOf(Component.literal("Rare Frost Wisp")),
			true
		) { id -> if (id == "PET-FROST_WISP-UNCOMMON") 5_000_000.0 else null }

		assertTrue(lines[1].contains("Uncommon Frost Wisp"))
		assertTrue(lines[1].contains("5,000,000"))
	}

	@Test
	fun `Hide Pet Level drops the level at any level`() {
		assertEquals("§6Ammonite", rewrite(AMMONITE, hideLevel = true, hideMaxLevel = false))
		assertEquals("§dEndermite§5 ✦", rewrite(ENDERMITE, hideLevel = true, hideMaxLevel = false))
	}

	@Test
	fun `Hide Max Pet Level only drops the level at 100 and 200`() {
		assertEquals(AMMONITE, rewrite(AMMONITE, hideLevel = false, hideMaxLevel = true))
		assertEquals("§dEndermite§5 ✦", rewrite(ENDERMITE, hideLevel = false, hideMaxLevel = true))
		assertEquals("§6Golden Dragon", rewrite(DRAGON, hideLevel = false, hideMaxLevel = true))
	}

	@Test
	fun `a skin marker survives the rewrite`() {
		assertEquals("§8[§7Lv100§8] §dEndermite§5 ✦", rewrite(ENDERMITE, hideLevel = false, hideMaxLevel = false))
	}

	@Test
	fun `a stand the pattern does not recognise keeps the very component it arrived with`() {
		val plain = Component.literal("Hologram")
		val trailing = Component.literal("§8[§7Lv99§8] §6Ammonite§7 the Third")

		assertSame(plain, nametags.rewrite(7, plain, hideLevel = true, hideMaxLevel = false))
		assertSame(trailing, nametags.rewrite(8, trailing, hideLevel = true, hideMaxLevel = false))
	}

	@Test
	fun `the same stand and name is answered from the memo rather than rebuilt`() {
		val name = Component.literal(AMMONITE)
		val first = nametags.rewrite(7, name, hideLevel = true, hideMaxLevel = false)

		assertSame(first, nametags.rewrite(7, Component.literal(AMMONITE), hideLevel = true, hideMaxLevel = false))
	}

	@Test
	fun `flipping a hider on the same stand rebuilds the name`() {
		val name = Component.literal(AMMONITE)

		assertEquals("§6Ammonite", nametags.rewrite(7, name, hideLevel = true, hideMaxLevel = false).string)
		assertEquals(AMMONITE, nametags.rewrite(7, name, hideLevel = false, hideMaxLevel = false).string)
	}

	@Test
	fun `renaming a stand rebuilds the name`() {
		assertEquals("§6Ammonite", nametags.rewrite(7, Component.literal(AMMONITE), true, false).string)
		assertEquals("§6Golden Dragon", nametags.rewrite(7, Component.literal(DRAGON), true, false).string)
	}

	@Test
	fun `the pattern reads a nametag rebuilt from styled siblings`() {
		val styled = Component.empty()
			.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal("Lv99").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal("Ammonite").withStyle(ChatFormatting.GOLD))

		assertEquals("§6Ammonite", nametags.rewrite(7, styled, hideLevel = true, hideMaxLevel = false).string)
	}

	@Test
	fun `a level is read out of a pet's menu name and a Golden Dragon alone is maxed at 200`() {
		assertEquals(99, PetLines.level("§7[Lvl 99] §6Ammonite"))
		assertEquals(PetLines.UNKNOWN_LEVEL, PetLines.level("§6Ammonite"))
		assertEquals(PetLines.UNKNOWN_LEVEL, PetLines.level("§7[Lvl 99999999999] §6Ammonite"))
		assertFalse(PetLines.maxed(99))
		assertTrue(PetLines.maxed(100))
		assertTrue(PetLines.maxed(200))
		assertFalse(PetLines.maxed(100, "GOLDEN_DRAGON"))
		assertTrue(PetLines.maxed(200, "GOLDEN_DRAGON"))
		assertTrue(PetLines.maxed(100, "AMMONITE"))
		assertFalse(PetLines.maxed(99, "AMMONITE"))
	}

	private fun rewrite(styled: String, hideLevel: Boolean, hideMaxLevel: Boolean): String =
		nametags.rewrite(STAND, Component.literal(styled), hideLevel, hideMaxLevel).string

	private fun pet(type: String, tier: String, exp: Double): PetInfo = PetInfo(type, tier, exp, null, 0, null)

	private companion object {
		const val STAND = 7
		const val AMMONITE = "§8[§7Lv99§8] §6Ammonite"
		const val ENDERMITE = "§8[§7Lv100§8] §dEndermite§5 ✦"
		const val DRAGON = "§8[§7Lv200§8] §6Golden Dragon"
	}
}
