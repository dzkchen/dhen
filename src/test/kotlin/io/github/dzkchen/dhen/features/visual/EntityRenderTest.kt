package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.features.dungeon.ClassColors
import io.github.dzkchen.dhen.features.dungeon.DungeonClass
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.render.HighlightStyle
import net.minecraft.ChatFormatting
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EntityRenderTest {
	@BeforeEach
	@AfterEach
	fun reset() {
		for (module in listOf(RenderOptimizer, DamageSplash, NametagTweaks, HidePlayers, EntityHighlight, Box3D, MobHighlight, ClassColors)) {
			for (setting in module.settings) setting.reset()
		}
	}

	@Test
	fun `the eight modules declare the categories and names the roadmap names`() {
		assertEquals("Render Optimizer" to Category.VISUAL, RenderOptimizer.name to RenderOptimizer.category)
		assertEquals("Damage Splash" to Category.VISUAL, DamageSplash.name to DamageSplash.category)
		assertEquals("Nametag Tweaks" to Category.VISUAL, NametagTweaks.name to NametagTweaks.category)
		assertEquals("Hide Players" to Category.VISUAL, HidePlayers.name to HidePlayers.category)
		assertEquals("Entity Highlight" to Category.VISUAL, EntityHighlight.name to EntityHighlight.category)
		assertEquals("Box ESP" to Category.VISUAL, Box3D.name to Box3D.category)
		assertEquals("Mob Highlight" to Category.COMBAT, MobHighlight.name to MobHighlight.category)
		assertEquals("Class Colors" to Category.DUNGEONS, ClassColors.name to ClassColors.category)
	}

	@Test
	fun `every nametag tweak stays off until its module is enabled`() {
		for (setting in NametagTweaks.settings) (setting as BooleanSetting).value = true
		RenderOptimizer.hideFireSetting.value = true

		assertFalse(NametagTweaks.forcesNametags())
		assertFalse(NametagTweaks.hidesNametagBackground())
		assertFalse(NametagTweaks.shadowsNametagText())
		assertFalse(RenderOptimizer.hidesFireOnEntities())
	}

	@Test
	fun `the damage number shortens the way the source's formatter does`() {
		assertEquals("999", DamageSplash.shorten(999))
		assertEquals("1k", DamageSplash.shorten(1_000))
		assertEquals("1.5k", DamageSplash.shorten(1_500))
		assertEquals("15k", DamageSplash.shorten(15_000))
		assertEquals("999k", DamageSplash.shorten(999_999))
		assertEquals("1m", DamageSplash.shorten(1_000_000))
		assertEquals("1.2m", DamageSplash.shorten(1_234_567))
		assertEquals("12m", DamageSplash.shorten(12_345_678))
		assertEquals("1b", DamageSplash.shorten(1_000_000_000))
	}

	@Test
	fun `a critical splash scatters its colours and a normal one takes the flat code`() {
		assertEquals("§312m", DamageSplash.splash("12,345,678", "§712,345,678"))

		val critical = DamageSplash.splash("1,500", "§f✧1,500§f✧")

		assertTrue(critical.startsWith("§f✧")) { critical }
		assertTrue(critical.endsWith("§f✧")) { critical }
		assertEquals("1.5k", critical.removeSurrounding("§f✧", "§f✧").replace(COLOR_CODE, ""))
	}

	@Test
	fun `uppercase formatting reaches the shortened number`() {
		DamageSplash.uppercaseSetting.value = true

		assertEquals("§31.5K", DamageSplash.splash("1,500", "§71,500"))
	}

	@Test
	fun `each render style name maps to the box the engine draws`() {
		assertEquals(HighlightStyle.OUTLINE, EntityHighlight.boxStyle())

		EntityHighlight.styleSetting.value = "Filled"
		assertEquals(HighlightStyle.FILLED, EntityHighlight.boxStyle())

		EntityHighlight.styleSetting.value = "Filled Outline"
		assertEquals(HighlightStyle.FILLED_OUTLINE, EntityHighlight.boxStyle())
	}

	@Test
	fun `a class reads its source default while Class Colors is off and the pick once it is on`() {
		assertEquals(ChatFormatting.DARK_RED, DungeonClass.ARCHER.formatting)
		assertEquals(ChatFormatting.GOLD, DungeonClass.BERSERK.formatting)
		assertEquals(ChatFormatting.DARK_PURPLE, DungeonClass.HEALER.formatting)
		assertEquals(ChatFormatting.DARK_AQUA, DungeonClass.MAGE.formatting)
		assertEquals(ChatFormatting.DARK_GREEN, DungeonClass.TANK.formatting)
		assertEquals(ChatFormatting.BLACK, DungeonClass.EMPTY.formatting)

		ClassColors.setEnabled(true)
		try {
			assertEquals(ChatFormatting.DARK_RED, DungeonClass.ARCHER.formatting)
			assertEquals("§4", DungeonClass.ARCHER.code)
			assertEquals(0xFFAA0000u.toInt(), DungeonClass.ARCHER.color)

			ClassColors.choices[DungeonClass.ARCHER.ordinal].value = "Aqua"

			assertEquals(ChatFormatting.AQUA, DungeonClass.ARCHER.formatting)
			assertEquals("§b", DungeonClass.ARCHER.code)

			ClassColors.resetSetting.value()

			assertEquals(ChatFormatting.DARK_RED, DungeonClass.ARCHER.formatting)
		} finally {
			ClassColors.setEnabled(false)
		}
	}

	@Test
	fun `an unknown class name falls back to the empty slot`() {
		assertEquals(DungeonClass.TANK, DungeonClass.of("tank"))
		assertEquals(DungeonClass.EMPTY, DungeonClass.of("Necromancer"))
	}

	@Test
	fun `the sliders Box ESP cannot use in a mode are hidden in it`() {
		Box3D.modeSetting.value = "Fill"

		assertFalse(Box3D.lineWidthSetting.isVisible)
		assertFalse(Box3D.outlineOpacitySetting.isVisible)
		assertTrue(Box3D.fillOpacitySetting.isVisible)

		Box3D.modeSetting.value = "Outline"

		assertTrue(Box3D.lineWidthSetting.isVisible)
		assertTrue(Box3D.outlineOpacitySetting.isVisible)
		assertFalse(Box3D.fillOpacitySetting.isVisible)
	}

	@Test
	fun `the hide distance disappears once every player is hidden`() {
		assertTrue(HidePlayers.distanceSetting.isVisible)
		assertEquals(3.0, HidePlayers.distanceSetting.amount)

		HidePlayers.hideAllSetting.value = true

		assertFalse(HidePlayers.distanceSetting.isVisible)
	}

	private companion object {
		val COLOR_CODE = Regex("§.")
	}
}
