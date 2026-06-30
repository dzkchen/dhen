package io.github.dzkchen.dhen.api

enum class ModuleCategory {
	DUNGEONS,
	KUUDRA,
	MINING,
	FARMING,
	FISHING,
	SLAYER,
	BAZAAR_AUCTION,
	INVENTORY_QOL,
	HUD_OVERLAYS,
	CHAT,
	WAYPOINTS,
	UTILITY,
	GENERAL,
}

data class AddonMetadata(
	val id: AddonId,
	val name: String,
	val version: String,
	val authors: List<String> = emptyList(),
	val description: String = "",
)

data class ModuleMetadata(
	val id: ModuleId,
	val name: String,
	val description: String = "",
	val category: ModuleCategory = ModuleCategory.GENERAL,
	val settings: List<SettingSchema> = emptyList(),
	val keybinds: List<KeybindSpec> = emptyList(),
)

// Typed config schema. The schema is the source of truth for defaults and validation;
// GUI code never duplicates default values. Extended with more setting types beyond the
// vertical slice (only Boolean is needed for the first module).
sealed interface SettingSchema {
	val id: String
	val name: String
	val description: String
}

data class BooleanSetting(
	override val id: String,
	override val name: String,
	override val description: String = "",
	val default: Boolean = false,
) : SettingSchema

// Platform-agnostic keybind descriptor. The default key is a GLFW key code; -1 means unbound.
data class KeybindSpec(
	val id: String,
	val displayName: String,
	val defaultKey: Int = -1,
)
