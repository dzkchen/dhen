package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.features.dungeon.legacyColor
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.util.Color
import net.minecraft.ChatFormatting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FireFreezeTest {
	@BeforeEach
	fun reset() {
		for (setting in FireFreeze.settings) setting.reset()
		FireFreeze.forget()
	}

	@Test
	fun `the module declares the five controls its source exposes`() {
		assertEquals("Fire Freeze", FireFreeze.name)
		assertEquals(Category.COMBAT, FireFreeze.category)
		assertEquals(
			listOf("Freeze Timer", "Mob Timer", "Box Frozen Mobs", "Custom Circle", "Freeze Circle Color"),
			FireFreeze.settings.map { it.name }
		)
		assertFalse(FireFreeze.mobHighlightSetting.default)
		assertFalse(FireFreeze.customCircleSetting.default)
		assertEquals(Color.rgba(0, 0, 0, 245), FireFreeze.colorSetting.default)
	}

	@Test
	fun `the first pitch estimates two seconds per pitch step plus one`() {
		val area = FreezeArea(0.0, 64.0, 0.0, 2f, 100L)
		assertEquals(100L + 100L, area.startTick)
		assertEquals(1f, FreezeArea(0.0, 64.0, 0.0, 1f, 0L).startTick / 60f)
	}

	@Test
	fun `the next different pitch refines the estimate exactly once`() {
		val area = FreezeArea(0.0, 64.0, 0.0, 2f, 0L)
		area.refine(2f, 40L)
		assertEquals(100L, area.startTick)
		area.refine(1.5f, 40L)
		assertEquals(40L + 80L, area.startTick)
		area.refine(1f, 80L)
		assertEquals(120L, area.startTick)
	}

	@Test
	fun `an area stops drawing half a second after it should have landed`() {
		val area = FreezeArea(0.0, 64.0, 0.0, 0f, 0L)
		assertEquals(20L, area.startTick)
		assertFalse(area.finished(30L))
		assertTrue(area.finished(31L))
	}

	@Test
	fun `the radius ignores height and takes its slack from the caller`() {
		val area = FreezeArea(0.0, 64.0, 0.0, 0f, 0L)
		assertTrue(area.inside(4.9, 0.0, 0.0))
		assertFalse(area.inside(5.0, 0.0, 0.0))
		assertTrue(area.inside(5.4, 0.0, 0.5))
		assertFalse(area.inside(5.5, 0.0, 0.5))
	}

	@Test
	fun `an area is matched by the exact location the sound named`() {
		val area = FreezeArea(1.5, 64.25, -3.75, 0f, 0L)
		assertTrue(area.at(1.5, 64.25, -3.75))
		assertFalse(area.at(1.5, 64.25, -3.5))
	}

	@Test
	fun `a countdown is written to a tenth of a second`() {
		val area = FreezeArea(0.0, 64.0, 0.0, 2f, 0L)
		area.refresh(0L)
		assertEquals("❄ 5.0s", area.label)
		area.refresh(35L)
		assertEquals("❄ 3.2s", area.label)
		area.refresh(100L)
		assertEquals("❄ 0.0s", area.label)
	}

	@Test
	fun `an armed area covers the particles around it until it lands`() {
		FireFreeze.armed(0.0, 64.0, 0.0, 2f, 0L)
		assertTrue(FireFreeze.covering(5.4, 0.0))
		assertFalse(FireFreeze.covering(5.6, 0.0))
		FireFreeze.armed(0.0, 64.0, 0.0, 1.5f, 0L)
		assertTrue(FireFreeze.covering(0.0, 0.0))
	}

	@Test
	fun `a thunder spark blocks an arming sound only within two blocks of it`() {
		assertTrue(FireFreeze.nearSpark(1.0, 1.0, 1.0))
		assertTrue(FireFreeze.nearSpark(0.0, 1.9, 0.0))
		assertFalse(FireFreeze.nearSpark(2.0, 0.0, 0.0))
		assertFalse(FireFreeze.nearSpark(0.0, 1.5, 1.5))
		assertFalse(FireFreeze.nearSpark(-1.5, -1.5, 0.0))
	}

	@Test
	fun `a frozen mob is yellow for its first five seconds and red for its last`() {
		val mobs = FrozenMobs()
		assertEquals(legacyColor(ChatFormatting.YELLOW), mobs.inkFor(FireFreeze.FREEZE_TICKS))
		assertEquals(legacyColor(ChatFormatting.RED), mobs.inkFor(100L))
		assertEquals(legacyColor(ChatFormatting.RED), mobs.inkFor(1L))
		val middle = Color(mobs.inkFor(150L))
		assertTrue(middle.green in 86..254)
	}
}
