package io.github.dzkchen.dhen.example.dungeonmap

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.ChatReceiveEvent
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.EventSubscription
import io.github.dzkchen.dhen.api.HudWidgetSpec
import io.github.dzkchen.dhen.api.onEvent
import io.github.dzkchen.dhen.api.KeybindId
import io.github.dzkchen.dhen.api.KeybindSpec
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleMetadata
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.WidgetId

// Native JAR example addon. Discovered by the Dhen host after restart and registers one module.
private val ADDON_ID = AddonId("dhen.dungeon-map")
private val GREETER_MODULE_ID = ModuleId("dhen.dungeon-map:greeter")
private val SHOW_HUD_SETTING_ID = SettingId("show_hud")
private val GREET_KEYBIND_ID = KeybindId("greet")
private val HUD_WIDGET_ID = WidgetId("hud")

class DungeonMapAddon : DhenAddon {
	override val metadata: AddonMetadata = AddonMetadata(
		id = ADDON_ID,
		name = "Dhen Dungeon Map",
		version = "1.0.0",
		authors = listOf("dzkchen"),
		description = "Native JAR example addon for the Dhen host (map-style UI placeholder).",
		providedModules = listOf(GREETER_MODULE_ID),
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
		id = GREETER_MODULE_ID,
		name = "Greeter",
		description = "Shows a HUD line and greets in chat when the keybind is pressed.",
		category = ModuleCategory.HUD_OVERLAYS,
		settings = listOf(
			BooleanSetting(
				id = SHOW_HUD_SETTING_ID,
				name = "Show HUD line",
				description = "Render the greeter line on the HUD.",
				default = true,
			),
		),
		hudWidgets = listOf(HudWidgetSpec(id = HUD_WIDGET_ID, name = "Greeter HUD")),
		keybinds = listOf(KeybindSpec(id = GREET_KEYBIND_ID, displayName = "Dhen: Greet", defaultKey = GLFW_KEY_G)),
		eventSubscriptions = listOf(EventSubscription("chat.receive", "Counts received chat/game messages.")),
	)

	private var messages = 0

	override fun onEnable(context: ModuleEnableContext) {
		context.addHudText(HUD_WIDGET_ID) {
			if (context.booleanSetting(SHOW_HUD_SETTING_ID)) "Dhen Greeter active — greets: $greetings, msgs: $messages" else null
		}
		context.onKeybind(GREET_KEYBIND_ID) {
			greetings++
			context.sendChat("[Dhen] Greeter says hi! (#$greetings)")
		}
		context.onEvent<ChatReceiveEvent> { messages++ }
		context.logger.info("Greeter enabled")
	}

	override fun onDisable(context: io.github.dzkchen.dhen.api.ModuleDisableContext) {
		context.logger.info("Greeter disabled")
	}
}
