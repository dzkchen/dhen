package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.util.ARGB
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class VisualTweaksTest {
	@AfterEach
	fun reset() {
		for (setting in VisualTweaks.settings) setting.reset()
	}

	@Test
	fun `one module declares both sub-features in order`() {
		assertEquals("Visual Tweaks", VisualTweaks.name)
		assertEquals(Category.VISUAL, VisualTweaks.category)
		assertEquals(1, VisualTweaks.subscriptionCount)
		assertEquals(
			listOf(
				"Lava to Water",
				"Color Tint",
				"Tint Color",
				"Hide Fog",
				"See Through Water",
				"Dark Mode",
				"Opacity",
				"Tint HUD"
			),
			VisualTweaks.settings.map { it.name }
		)
		assertFalse(VisualTweaks.lavaToWaterSetting.default)
		assertFalse(VisualTweaks.colorTintSetting.default)
		assertEquals(Color.rgba(63, 118, 228), VisualTweaks.tintColorSetting.default)
		assertTrue(VisualTweaks.hideFogSetting.default)
		assertFalse(VisualTweaks.seeThroughWaterSetting.default)
		assertFalse(VisualTweaks.darkModeSetting.default)
		assertEquals(25.0, VisualTweaks.opacitySetting.default)
		assertEquals(1.0, VisualTweaks.opacitySetting.min)
		assertEquals(80.0, VisualTweaks.opacitySetting.max)
		assertEquals(1.0, VisualTweaks.opacitySetting.step)
		assertFalse(VisualTweaks.tintHudSetting.default)
		assertEquals(ARGB.black(64), VisualTweaks.overlayColor())
	}

	@Test
	fun `each sub-feature hides its own settings until it is selected`() {
		for (setting in listOf(
			VisualTweaks.colorTintSetting,
			VisualTweaks.tintColorSetting,
			VisualTweaks.hideFogSetting,
			VisualTweaks.seeThroughWaterSetting,
			VisualTweaks.opacitySetting,
			VisualTweaks.tintHudSetting
		)) assertFalse(setting.isVisible, setting.name)
		assertTrue(VisualTweaks.lavaToWaterSetting.isVisible)
		assertTrue(VisualTweaks.darkModeSetting.isVisible)

		VisualTweaks.lavaToWaterSetting.on = true

		assertTrue(VisualTweaks.colorTintSetting.isVisible)
		assertTrue(VisualTweaks.hideFogSetting.isVisible)
		assertTrue(VisualTweaks.seeThroughWaterSetting.isVisible)
		assertFalse(VisualTweaks.tintColorSetting.isVisible)
		VisualTweaks.colorTintSetting.on = true
		assertTrue(VisualTweaks.tintColorSetting.isVisible)

		VisualTweaks.darkModeSetting.on = true

		assertTrue(VisualTweaks.opacitySetting.isVisible)
		assertTrue(VisualTweaks.tintHudSetting.isVisible)
	}

	@Test
	fun `all eight controls round trip with their exact values`() {
		VisualTweaks.lavaToWaterSetting.on = true
		VisualTweaks.colorTintSetting.on = true
		VisualTweaks.tintColorSetting.value = Color.rgba(12, 34, 56)
		VisualTweaks.hideFogSetting.on = false
		VisualTweaks.seeThroughWaterSetting.on = true
		VisualTweaks.darkModeSetting.on = true
		VisualTweaks.opacitySetting.amount = 67.0
		VisualTweaks.tintHudSetting.on = true
		val stored = SettingCodec.writeInto(JsonObject(), VisualTweaks.settings)

		for (setting in VisualTweaks.settings) setting.reset()
		SettingCodec.readInto(stored, VisualTweaks.settings, VisualTweaks.name)

		assertTrue(VisualTweaks.lavaToWaterSetting.on)
		assertTrue(VisualTweaks.colorTintSetting.on)
		assertEquals(Color.rgba(12, 34, 56), VisualTweaks.tintColorSetting.value)
		assertFalse(VisualTweaks.hideFogSetting.on)
		assertTrue(VisualTweaks.seeThroughWaterSetting.on)
		assertTrue(VisualTweaks.darkModeSetting.on)
		assertEquals(67.0, VisualTweaks.opacitySetting.amount)
		assertTrue(VisualTweaks.tintHudSetting.on)
	}

	@Test
	fun `the two retired module blocks fold into one with their toggles preserved`(@TempDir dir: Path) {
		val path = dir.resolve("modules.json")
		Files.writeString(
			path,
			"""{"version":2,"modules":{"Lava to Water":{"enabled":false,"settings":{"Color Tint":true,""" +
				""""Tint Color":-12618012,"Hide Fog":false,"See Through Water":false}},""" +
				""""Dark Mode":{"enabled":true,"settings":{"Opacity":23.0,"Tint HUD":true}},"Sample":{"enabled":true}}}"""
		)

		val loaded = ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations).load()

		val modules = loaded.getAsJsonObject("modules")
		assertFalse(modules.has("Lava to Water"))
		assertFalse(modules.has("Dark Mode"))
		assertTrue(modules.has("Sample"))
		val merged = modules.getAsJsonObject("Visual Tweaks")
		assertTrue(merged.get("enabled").asBoolean)
		val settings = merged.getAsJsonObject("settings")
		assertFalse(settings.get("Lava to Water").asBoolean)
		assertTrue(settings.get("Color Tint").asBoolean)
		assertEquals(-12618012, settings.get("Tint Color").asInt)
		assertFalse(settings.get("Hide Fog").asBoolean)
		assertFalse(settings.get("See Through Water").asBoolean)
		assertTrue(settings.get("Dark Mode").asBoolean)
		assertEquals(23.0, settings.get("Opacity").asDouble)
		assertTrue(settings.get("Tint HUD").asBoolean)
		assertEquals(ModulePersistence.version, loaded.get("version").asInt)
	}

	@Test
	fun `a config with neither retired block is left alone`(@TempDir dir: Path) {
		val path = dir.resolve("modules.json")
		Files.writeString(path, """{"version":2,"modules":{"Sample":{"enabled":true}}}""")

		val loaded = ConfigStore(path, CoroutineScope(Dispatchers.IO), migrations = ModulePersistence.migrations).load()

		val modules = loaded.getAsJsonObject("modules")
		assertFalse(modules.has("Visual Tweaks"))
		assertTrue(modules.has("Sample"))
	}

	@Test
	fun `see through mode preserves rgb at half alpha`() {
		val color = ARGB.color(255, 12, 34, 56)
		assertEquals(ARGB.color(128, 12, 34, 56), VisualTweaks.transparent(color))
	}

	@Test
	fun `water and lava routing obey the sub-feature setting`() = withModule {
		assertFalse(VisualTweaks.rendersLavaAsWater())
		VisualTweaks.lavaToWaterSetting.on = true
		assertTrue(VisualTweaks.rendersLavaAsWater())

		assertFalse(VisualTweaks.usesWaterModel(Fluids.WATER))
		assertTrue(VisualTweaks.usesWaterModel(Fluids.LAVA))
		assertFalse(VisualTweaks.usesWaterModel(Fluids.EMPTY))

		VisualTweaks.seeThroughWaterSetting.on = true

		assertTrue(VisualTweaks.usesWaterModel(Fluids.WATER))
		assertTrue(VisualTweaks.usesWaterModel(Fluids.FLOWING_WATER))
	}

	@Test
	fun `every lava setting changes the signature the tick poll compares`() = withModule {
		val idle = VisualTweaks.modelSignature()
		VisualTweaks.lavaToWaterSetting.on = true
		val lava = VisualTweaks.modelSignature()
		assertNotEquals(idle, lava)

		VisualTweaks.colorTintSetting.on = true
		val tinted = VisualTweaks.modelSignature()
		assertNotEquals(lava, tinted)

		VisualTweaks.tintColorSetting.value = Color.rgba(9, 10, 11)
		val recolored = VisualTweaks.modelSignature()
		assertNotEquals(tinted, recolored)

		VisualTweaks.seeThroughWaterSetting.on = true
		assertNotEquals(recolored, VisualTweaks.modelSignature())

		VisualTweaks.lavaToWaterSetting.on = false
		assertNotEquals(recolored, VisualTweaks.modelSignature())
	}

	@Test
	fun `lava fog follows hide and tint settings`() = withModule {
		assertFalse(VisualTweaks.shouldHideFog())
		VisualTweaks.lavaToWaterSetting.on = true
		assertTrue(VisualTweaks.shouldHideFog())

		val fog = FogData().apply { color.w = 1.0f }
		VisualTweaks.hideFog(fog, 24.0f)
		assertEquals(0.0f, fog.color.w)
		assertEquals(24.0f, fog.environmentalStart)
		assertEquals(24.0f, fog.environmentalEnd)

		assertFalse(VisualTweaks.tintsFog())
		VisualTweaks.colorTintSetting.on = true
		VisualTweaks.tintColorSetting.value = Color.rgba(9, 10, 11)
		assertTrue(VisualTweaks.tintsFog())
		assertEquals(Color.rgba(9, 10, 11).argb, VisualTweaks.fogTint())

		VisualTweaks.hideFogSetting.on = false
		assertFalse(VisualTweaks.shouldHideFog())
	}

	@Test
	fun `exactly one dark mode arm draws in each frame`() {
		assertFalse(VisualTweaks.drawsBehindHud())
		assertFalse(VisualTweaks.drawsOverHud(deferred = true, afterDeferredSubtitles = true, hudExtracted = true))
		withModule {
			VisualTweaks.darkModeSetting.on = true

			assertTrue(VisualTweaks.drawsBehindHud())
			assertFalse(VisualTweaks.drawsOverHud(deferred = true, afterDeferredSubtitles = true, hudExtracted = true))
			assertFalse(VisualTweaks.drawsOverHud(deferred = false, afterDeferredSubtitles = false, hudExtracted = true))

			VisualTweaks.tintHudSetting.on = true

			assertFalse(VisualTweaks.drawsBehindHud())
			assertTrue(VisualTweaks.drawsOverHud(deferred = true, afterDeferredSubtitles = true, hudExtracted = true))
			assertFalse(VisualTweaks.drawsOverHud(deferred = true, afterDeferredSubtitles = false, hudExtracted = true))
			assertTrue(VisualTweaks.drawsOverHud(deferred = false, afterDeferredSubtitles = false, hudExtracted = true))
			assertFalse(VisualTweaks.drawsOverHud(deferred = false, afterDeferredSubtitles = true, hudExtracted = true))
		}
	}

	@Test
	fun `the deferred arm stands down when no hud was extracted`() = withModule {
		VisualTweaks.darkModeSetting.on = true
		VisualTweaks.tintHudSetting.on = true

		assertTrue(VisualTweaks.drawsOverHud(deferred = true, afterDeferredSubtitles = true, hudExtracted = true))
		assertFalse(VisualTweaks.drawsOverHud(deferred = true, afterDeferredSubtitles = true, hudExtracted = false))
	}

	private inline fun withModule(block: () -> Unit) {
		val manager = ModuleManager()
		manager.register(VisualTweaks)
		try {
			manager.enable(VisualTweaks)
			block()
		} finally {
			manager.unregister(VisualTweaks)
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
