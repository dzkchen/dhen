package io.github.dzkchen.dhen.addon.template

import io.github.dzkchen.dhen.api.AddonContext
import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.AddonMetadata
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.api.DhenModule
import io.github.dzkchen.dhen.api.ModuleCategory
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.ModuleMetadata

// Starter native addon. Copy this module, give it a stable id under your own addon id, and register
// the behaviors you need through the enable context.
class TemplateAddon : DhenAddon {
	override val metadata: AddonMetadata = AddonMetadata(
		id = AddonId("dhen.template"),
		name = "Dhen Addon Template",
		version = "1.0.0",
		authors = listOf("dzkchen"),
		description = "Starter native Fabric JAR addon for the Dhen host.",
	)

	override fun register(context: AddonContext) {
		context.registerModule(TemplateModule())
	}
}

class TemplateModule : DhenModule {
	override val metadata: ModuleMetadata = ModuleMetadata(
		id = ModuleId("dhen.template:example"),
		name = "Example Module",
		description = "Replace this with your own module behavior.",
		category = ModuleCategory.GENERAL,
		settings = listOf(
			BooleanSetting(id = "enabled_message", name = "Log on enable", default = true),
		),
	)

	override fun onEnable(context: ModuleEnableContext) {
		if (context.booleanSetting("enabled_message")) {
			context.logger.info("Template example module enabled")
		}
	}
}
