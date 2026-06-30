package io.github.dzkchen.dhen.example.dungeonmap

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.HudWidgetSpec
import io.github.dzkchen.dhen.api.KeybindId
import io.github.dzkchen.dhen.api.KeybindSpec
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.WidgetId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DungeonMapAddonTest {
	@Test
	fun registersGreeterModule() {
		val context = CapturingAddonContext()

		DungeonMapAddon().register(context)

		val metadata = context.modules.single().metadata
		assertEquals(ModuleId("dhen.dungeon-map:greeter"), metadata.id)
		assertEquals(
			BooleanSetting(
				id = SettingId("show_hud"),
				name = "Show HUD line",
				description = "Render the greeter line on the HUD.",
				default = true,
			),
			metadata.settings.single(),
		)
		assertEquals(HudWidgetSpec(id = WidgetId("hud"), name = "Greeter HUD"), metadata.hudWidgets.single())
		assertEquals(KeybindSpec(id = KeybindId("greet"), displayName = "Dhen: Greet", defaultKey = 71), metadata.keybinds.single())
	}
}

private class CapturingAddonContext : AddonContext {
	override val addonId: AddonId = AddonId("dhen.dungeon-map")
	override val logger: AddonLogger = NoopLogger
	val modules = mutableListOf<DhenModule>()

	override fun registerModule(module: DhenModule) {
		modules += module
	}
}

private object NoopLogger : AddonLogger {
	override fun info(message: String) = Unit
	override fun warn(message: String) = Unit
	override fun error(message: String, throwable: Throwable?) = Unit
}
