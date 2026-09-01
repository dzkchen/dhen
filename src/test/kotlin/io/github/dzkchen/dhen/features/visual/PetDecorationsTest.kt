package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.pet.PetLines
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PetDecorationsTest {
	private val nametags = PetNametags()

	@Test
	fun `both hiders off leaves the level in front of the name`() {
		assertEquals(AMMONITE, rewrite(AMMONITE, hideLevel = false, hideMaxLevel = false))
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
	fun `a name that is not a pet nametag is left alone`() {
		assertNull(nametags.rewritten("§eClick to open", hideLevel = true, hideMaxLevel = false))
		assertNull(nametags.rewritten("§8[§7Lv99§8] §6Ammonite§7 the Third", hideLevel = true, hideMaxLevel = false))
	}

	@Test
	fun `an unrecognised stand keeps the very component it arrived with`() {
		val plain = Component.literal("Hologram")

		assertSame(plain, nametags.rewrite(7, plain, hideLevel = true, hideMaxLevel = false))
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
	fun `a level is read out of a pet's menu name and only 100 and 200 count as maxed`() {
		assertEquals(99, PetLines.level("§7[Lvl 99] §6Ammonite"))
		assertEquals(PetLines.UNKNOWN_LEVEL, PetLines.level("§6Ammonite"))
		assertEquals(PetLines.UNKNOWN_LEVEL, PetLines.level("§7[Lvl 99999999999] §6Ammonite"))
		assertFalse(PetLines.maxed(99))
		assertTrue(PetLines.maxed(100))
		assertTrue(PetLines.maxed(200))
	}

	@Test
	fun `a Golden Dragon is only maxed at 200, where every other pet is maxed at 100`() {
		assertFalse(PetLines.maxed(100, "GOLDEN_DRAGON"))
		assertFalse(PetLines.maxed(199, "GOLDEN_DRAGON"))
		assertTrue(PetLines.maxed(200, "GOLDEN_DRAGON"))
		assertTrue(PetLines.maxed(100, "AMMONITE"))
		assertFalse(PetLines.maxed(99, "AMMONITE"))
	}

	private fun rewrite(styled: String, hideLevel: Boolean, hideMaxLevel: Boolean): String =
		nametags.rewritten(styled, hideLevel, hideMaxLevel)?.string ?: styled

	private companion object {
		const val AMMONITE = "§8[§7Lv99§8] §6Ammonite"
		const val ENDERMITE = "§8[§7Lv100§8] §dEndermite§5 ✦"
		const val DRAGON = "§8[§7Lv200§8] §6Golden Dragon"
	}
}
