package io.github.dzkchen.dhen.api

// Stable identifiers. Lowercase ASCII, stored in config instead of display names.

private const val SEGMENT = "[a-z0-9]+([._-][a-z0-9]+)*"
private val LOCAL_ID_REGEX = Regex(SEGMENT)
private val ADDON_ID_REGEX = LOCAL_ID_REGEX
private val MODULE_ID_REGEX = Regex("$SEGMENT:$SEGMENT")

private fun requireLocalId(kind: String, value: String) {
	require(LOCAL_ID_REGEX.matches(value)) { "Invalid $kind id: '$value' (expected lowercase ASCII, e.g. 'show_hud')" }
}

@JvmInline
value class AddonId(val value: String) {
	init {
		require(ADDON_ID_REGEX.matches(value)) { "Invalid addon id: '$value' (expected lowercase ASCII, e.g. 'dhen.dungeon-map')" }
	}

	override fun toString(): String = value
}

@JvmInline
value class ModuleId(val value: String) {
	init {
		require(MODULE_ID_REGEX.matches(value)) { "Invalid module id: '$value' (expected 'addonId:module-path')" }
	}

	val addonId: AddonId get() = AddonId(value.substringBefore(':'))

	override fun toString(): String = value
}

@JvmInline
value class SettingId(val value: String) {
	init {
		requireLocalId("setting", value)
	}

	override fun toString(): String = value
}

@JvmInline
value class WidgetId(val value: String) {
	init {
		requireLocalId("widget", value)
	}

	override fun toString(): String = value
}

@JvmInline
value class KeybindId(val value: String) {
	init {
		requireLocalId("keybind", value)
	}

	override fun toString(): String = value
}
