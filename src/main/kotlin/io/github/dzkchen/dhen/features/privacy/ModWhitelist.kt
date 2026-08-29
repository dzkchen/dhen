package io.github.dzkchen.dhen.features.privacy

import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting.Companion.derived
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.privacy.ModRegistry
import io.github.dzkchen.dhen.privacy.Whitelist

object ModWhitelist : Module(
	name = "Mod Whitelist",
	category = Category.PRIVACY,
	description = "Chooses which of your mods a server is allowed to see."
) {
	internal val modeSetting = SelectorSetting(
		"Mode",
		Whitelist.AUTO,
		Whitelist.CHOOSABLE_MODES,
		listed = true,
		description = "Auto allows every mod that registered a channel; Custom allows only the mods you pick. " +
			"Locked to Block All while Spoof as Vanilla is on."
	).also { it.changed = ::recompute }
	private var chosenMode by modeSetting

	private val editing: () -> Boolean = { enabled && chosenMode == Whitelist.CUSTOM }

	init {
		registerSetting(
			ActionSetting("Enable All", { selectAll(true) }, "Adds every installed mod to the whitelist.")
				.withDependency(editing)
		)
		registerSetting(
			ActionSetting("Disable All", { selectAll(false) }, "Clears every mod you picked.")
				.withDependency(editing)
		)
	}

	private val entries: Map<String, SelectorSetting> = ModRegistry.whitelistable().associate { container ->
		val modId = container.metadata.id
		val setting = SelectorSetting(
			container.metadata.name,
			Whitelist.OFF,
			Whitelist.ENTRY_STATES,
			description = modId
		).derived().withDependency(editing)
		setting.changed = { chose(modId, setting) }
		registerSetting(setting)
		modId to setting
	}

	private var stored by StringSetting("Whitelisted").hide()

	private val explicit = LinkedHashSet<String>()
	private var restored = false

	override fun onEnabled() {
		recompute()
	}

	override fun onDisabled() {
		publishMode()
	}

	override fun onReset() {
		explicit.clear()
		recompute()
	}

	internal fun spoofingChanged(spoofing: Boolean) {
		modeSetting.options = Whitelist.modes(spoofing)
		publishMode()
	}

	private fun recompute() {
		if (!restored) {
			restored = true
			explicit += Whitelist.decode(stored)
		}
		ModRegistry.select(explicit)
		stored = Whitelist.encode(explicit)
		for ((modId, setting) in entries) refresh(modId, setting)
		publishMode()
	}

	private fun refresh(modId: String, setting: SelectorSetting) {
		val chosen = modId in explicit
		val requiredBy = if (chosen) null else ModRegistry.requiredBy(modId)?.let { entries[it]?.name ?: it }
		setting.options = Whitelist.states(requiredBy)
		setting.value = if (chosen) Whitelist.ON else Whitelist.OFF
		setting.description = Whitelist.describe(modId, requiredBy)
	}

	private fun chose(modId: String, setting: SelectorSetting) {
		if (setting.preferred == Whitelist.ON) explicit += modId else explicit -= modId
		recompute()
	}

	private fun selectAll(on: Boolean) {
		if (on) entries.keys.toCollection(explicit) else explicit.clear()
		recompute()
		persist()
	}

	private fun publishMode() {
		ModRegistry.mode = Whitelist.effectiveMode(SpoofAsVanilla.enabled, editing())
	}
}
