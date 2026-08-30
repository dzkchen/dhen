package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.util.ARGB
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VisualTweaksTest {
	@AfterEach
	fun reset() {
		for (setting in LavaToWater.settings) setting.reset()
		for (setting in DarkMode.settings) setting.reset()
	}

	@Test
	fun `lava to water declares its complete control surface`() {
		assertEquals("Lava to Water", LavaToWater.name)
		assertEquals(Category.VISUAL, LavaToWater.category)
		assertEquals(1, LavaToWater.subscriptionCount)
		assertEquals(
			listOf("Color Tint", "Tint Color", "Hide Fog", "See Through Water"),
			LavaToWater.settings.map { it.name }
		)
		assertFalse(LavaToWater.colorTintSetting.default)
		assertEquals(Color.rgba(63, 118, 228), LavaToWater.tintColorSetting.default)
		assertTrue(LavaToWater.hideFogSetting.default)
		assertFalse(LavaToWater.seeThroughWaterSetting.default)
	}

	@Test
	fun `tint color is visible only when tinting is selected`() {
		assertFalse(LavaToWater.tintColorSetting.isVisible)
		LavaToWater.colorTintSetting.on = true
		assertTrue(LavaToWater.tintColorSetting.isVisible)
	}

	@Test
	fun `visual settings round trip with their exact values`() {
		LavaToWater.colorTintSetting.on = true
		LavaToWater.tintColorSetting.value = Color.rgba(12, 34, 56)
		LavaToWater.hideFogSetting.on = false
		LavaToWater.seeThroughWaterSetting.on = true
		DarkMode.opacitySetting.amount = 67.0
		DarkMode.tintHudSetting.on = true
		val lava = SettingCodec.writeInto(JsonObject(), LavaToWater.settings)
		val dark = SettingCodec.writeInto(JsonObject(), DarkMode.settings)

		for (setting in LavaToWater.settings) setting.reset()
		for (setting in DarkMode.settings) setting.reset()
		SettingCodec.readInto(lava, LavaToWater.settings, LavaToWater.name)
		SettingCodec.readInto(dark, DarkMode.settings, DarkMode.name)

		assertTrue(LavaToWater.colorTintSetting.on)
		assertEquals(Color.rgba(12, 34, 56), LavaToWater.tintColorSetting.value)
		assertFalse(LavaToWater.hideFogSetting.on)
		assertTrue(LavaToWater.seeThroughWaterSetting.on)
		assertEquals(67.0, DarkMode.opacitySetting.amount)
		assertTrue(DarkMode.tintHudSetting.on)
	}

	@Test
	fun `see through mode preserves rgb at half alpha`() {
		val color = ARGB.color(255, 12, 34, 56)
		assertEquals(ARGB.color(128, 12, 34, 56), LavaToWater.transparent(color))
	}

	@Test
	fun `water and lava routing obey the module setting`() = withLavaToWater {
		assertFalse(LavaToWater.usesWaterModel(Fluids.WATER))
		assertTrue(LavaToWater.usesWaterModel(Fluids.LAVA))
		assertFalse(LavaToWater.usesWaterModel(Fluids.EMPTY))

		LavaToWater.seeThroughWaterSetting.on = true

		assertTrue(LavaToWater.usesWaterModel(Fluids.WATER))
		assertTrue(LavaToWater.usesWaterModel(Fluids.FLOWING_WATER))
	}

	@Test
	fun `lava fog follows hide and tint settings`() = withLavaToWater {
		assertTrue(LavaToWater.shouldHideFog())
		val fog = FogData().apply { color.w = 1.0f }
		LavaToWater.hideFog(fog, 24.0f)
		assertEquals(0.0f, fog.color.w)
		assertEquals(24.0f, fog.environmentalStart)
		assertEquals(24.0f, fog.environmentalEnd)

		val water = ARGB.color(255, 5, 6, 7)
		assertEquals(water, LavaToWater.fogColor(water))
		LavaToWater.colorTintSetting.on = true
		LavaToWater.tintColorSetting.value = Color.rgba(9, 10, 11)
		assertEquals(Color.rgba(9, 10, 11).argb, LavaToWater.fogColor(water))

		LavaToWater.hideFogSetting.on = false
		assertFalse(LavaToWater.shouldHideFog())
	}

	@Test
	fun `dark mode declares opacity and optional hud tint`() {
		assertEquals("Dark Mode", DarkMode.name)
		assertEquals(Category.VISUAL, DarkMode.category)
		assertEquals(listOf("Opacity", "Tint HUD"), DarkMode.settings.map { it.name })
		assertEquals(25.0, DarkMode.opacitySetting.default)
		assertEquals(1.0, DarkMode.opacitySetting.min)
		assertEquals(80.0, DarkMode.opacitySetting.max)
		assertEquals(1.0, DarkMode.opacitySetting.step)
		assertFalse(DarkMode.tintHudSetting.default)
		assertEquals(ARGB.black(64), DarkMode.overlayColor())
	}

	@Test
	fun `dark mode chooses exactly one side of the hud`() {
		assertFalse(DarkMode.drawsBehindHud())
		assertFalse(DarkMode.drawsOverHud())
		withDarkMode {
			assertTrue(DarkMode.drawsBehindHud())
			assertFalse(DarkMode.drawsOverHud())

			DarkMode.tintHudSetting.on = true

			assertFalse(DarkMode.drawsBehindHud())
			assertTrue(DarkMode.drawsOverHud())
		}
	}

	private inline fun withLavaToWater(block: () -> Unit) {
		val manager = ModuleManager()
		manager.register(LavaToWater)
		try {
			manager.enable(LavaToWater)
			block()
		} finally {
			manager.unregister(LavaToWater)
		}
	}

	private inline fun withDarkMode(block: () -> Unit) {
		val manager = ModuleManager()
		manager.register(DarkMode)
		try {
			manager.enable(DarkMode)
			block()
		} finally {
			manager.unregister(DarkMode)
		}
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			bootstrapMinecraft()
		}
	}
}
