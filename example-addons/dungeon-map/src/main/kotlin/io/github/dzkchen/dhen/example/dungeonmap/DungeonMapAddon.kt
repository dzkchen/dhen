package io.github.dzkchen.dhen.example.dungeonmap

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.KeybindSpec
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleMetadata
import io.github.dzkchen.dhen.api.ModuleId

// Native JAR example addon. Discovered by the Dhen host after restart and registers one module.
class DungeonMapAddon : DhenAddon {
	override val metadata: AddonMetadata = AddonMetadata(
		id = AddonId("dhen.dungeon-map"),
		name = "Dhen Dungeon Map",
		version = "1.0.0",
		authors = listOf("dzkchen"),
		description = "Native JAR example addon for the Dhen host (map-style UI placeholder).",
	)

	override fun register(context: AddonContext) {
		context.logger.info("Registering dungeon-map modules")
		context.registerModule(GreeterModule())
	}
}

// Just to test lolol, one module with one setting, one keybind, and one visible behavior: a HUD line plus a chat
private const val GLFW_KEY_G: Int = 71

class GreeterModule : DhenModule {
	private var greetings = 0

	override val metadata: ModuleMetadata = ModuleMetadata(
		id = ModuleId("dhen.dungeon-map:greeter"),
		name = "Greeter",
		description = "Shows a HUD line and greets in chat when the keybind is pressed.",
		category = ModuleCategory.HUD_OVERLAYS,
		settings = listOf(
			BooleanSetting(
				id = "show_hud",
				name = "Show HUD line",
				description = "Render the greeter line on the HUD.",
				default = true,
			),
		),
		keybinds = listOf(KeybindSpec(id = "greet", displayName = "Dhen: Greet", defaultKey = GLFW_KEY_G)),
	)

	override fun onEnable(context: ModuleEnableContext) {
		context.addHudText("hud") {
			if (context.booleanSetting("show_hud")) "Dhen Greeter active — greets: $greetings" else null
		}
		context.onKeybind("greet") {
			greetings++
			context.sendChat("[Dhen] Greeter says hi! (#$greetings)")
		}
		context.logger.info("Greeter enabled")
	}

	override fun onDisable(context: io.github.dzkchen.dhen.api.ModuleDisableContext) {
		context.logger.info("Greeter disabled")
	}
}
