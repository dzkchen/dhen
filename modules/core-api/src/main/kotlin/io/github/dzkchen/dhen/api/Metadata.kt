package io.github.dzkchen.dhen.api

import java.net.URI

enum class ModuleCategory(val displayName: String) {
	DUNGEONS("Dungeons"),
	KUUDRA("Kuudra"),
	MINING("Mining"),
	FARMING("Farming"),
	FISHING("Fishing"),
	SLAYER("Slayer"),
	BAZAAR_AUCTION("Bazaar/Auction House"),
	INVENTORY_QOL("Inventory/QOL"),
	HUD_OVERLAYS("HUD/Overlays"),
	CHAT("Chat"),
	WAYPOINTS("Waypoints"),
}

enum class AddonArtifactType(val displayName: String) {
	NATIVE_JAR("Native JAR"),
	SCRIPT("JavaScript script"),
}

private val RGB_COLOR_RANGE = 0..0xFFFFFF

data class AddonDependency(
	val id: AddonId,
	val versionRange: String = "*",
	val reason: String = "",
) {
	init {
		requireNotBlank("addon dependency versionRange", versionRange)
		VersionRange.parse(versionRange)
	}

	val parsedVersionRange: VersionRange get() = VersionRange.parse(versionRange)
}

data class AddonMetadata(
	val id: AddonId,
	val name: String,
	val version: String,
	val authors: List<String> = emptyList(),
	val description: String = "",
	val requiredDhenApi: String = "*",
	val artifactType: AddonArtifactType = AddonArtifactType.NATIVE_JAR,
	val sourceUrl: URI? = null,
	val minecraftVersionRange: String = "*",
	val fabricLoaderVersionRange: String = "*",
	val depends: List<AddonDependency> = emptyList(),
	val recommends: List<AddonDependency> = emptyList(),
	val breaks: List<AddonDependency> = emptyList(),
	val loadAfter: List<AddonId> = emptyList(),
	val providedModules: List<ModuleId> = emptyList(),
	val releaseNotes: String = "",
) {
	init {
		requireNotBlank("addon name", name)
		requireNotBlank("addon version", version)
		requireNotBlank("requiredDhenApi", requiredDhenApi)
		requireNotBlank("minecraftVersionRange", minecraftVersionRange)
		requireNotBlank("fabricLoaderVersionRange", fabricLoaderVersionRange)
		VersionRange.parse(requiredDhenApi)
		VersionRange.parse(minecraftVersionRange)
		VersionRange.parse(fabricLoaderVersionRange)
		requireAllNotBlank("authors", authors)
		require(sourceUrl == null || sourceUrl.isAbsolute) { "sourceUrl must be absolute when present" }
		requireUnique("depends", depends.map { it.id })
		requireUnique("recommends", recommends.map { it.id })
		requireUnique("breaks", breaks.map { it.id })
		requireUnique("loadAfter", loadAfter)
		requireUnique("providedModules", providedModules)
		require(providedModules.all { it.addonId == id }) { "providedModules must use addon id '$id'" }
	}

	val dependencies: List<AddonDependency> get() = depends
	val conflicts: List<AddonDependency> get() = breaks
	val requiredApiRange: String get() = requiredDhenApi
	val parsedRequiredApiRange: VersionRange get() = VersionRange.parse(requiredDhenApi)
	val parsedMinecraftVersionRange: VersionRange get() = VersionRange.parse(minecraftVersionRange)
	val parsedFabricLoaderVersionRange: VersionRange get() = VersionRange.parse(fabricLoaderVersionRange)
}

data class ModuleMetadata(
	val id: ModuleId,
	val name: String,
	val description: String = "",
	val category: ModuleCategory,
	val settings: List<SettingSchema> = emptyList(),
	val conflicts: List<ModuleId> = emptyList(),
	val optionalDependencies: List<ModuleId> = emptyList(),
	val eventSubscriptions: List<EventSubscription> = emptyList(),
	val hudWidgets: List<HudWidgetSpec> = emptyList(),
	val keybinds: List<KeybindSpec> = emptyList(),
	val commands: List<CommandSpec> = emptyList(),
) {
	init {
		requireNotBlank("module name", name)
		requireUnique("settings", settings.map { it.id })
		requireUnique("conflicts", conflicts)
		requireUnique("optionalDependencies", optionalDependencies)
		requireUnique("eventSubscriptions", eventSubscriptions.map { it.eventId })
		requireUnique("hudWidgets", hudWidgets.map { it.id })
		requireUnique("keybinds", keybinds.map { it.id })
		requireUnique("commands", commands.map { it.path })
		require(id !in conflicts) { "module cannot conflict with itself: $id" }
		require(id !in optionalDependencies) { "module cannot optionally depend on itself: $id" }
	}

	val configSchema: List<SettingSchema> get() = settings
}

// Typed config schema. The schema is the source of truth for defaults and validation;
// GUI code never duplicates default values. Extended with more setting types beyond the
// vertical slice (only Boolean is needed for the first module).
sealed interface SettingSchema {
	val id: SettingId
	val name: String
	val description: String
}

data class BooleanSetting(
	override val id: SettingId,
	override val name: String,
	override val description: String = "",
	val default: Boolean? = false,
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
	}
}

data class EnumSetting(
	override val id: SettingId,
	override val name: String,
	val values: List<String>,
	override val description: String = "",
	val default: String? = values.firstOrNull(),
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
		require(values.isNotEmpty()) { "enum setting values must not be empty" }
		requireAllNotBlank("enum setting values", values)
		requireUnique("enum setting values", values)
		require(default == null || default in values) { "enum setting default must be one of values" }
	}
}

data class IntRangeSetting(
	override val id: SettingId,
	override val name: String,
	val min: Int,
	val max: Int,
	override val description: String = "",
	val default: Int? = min,
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
		require(min <= max) { "int range setting min must be <= max" }
		require(default == null || default in min..max) { "int range setting default must be in range $min..$max" }
	}
}

data class FloatRangeSetting(
	override val id: SettingId,
	override val name: String,
	val min: Double,
	val max: Double,
	override val description: String = "",
	val default: Double? = min,
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
		require(min.isFinite()) { "float range setting min must be finite" }
		require(max.isFinite()) { "float range setting max must be finite" }
		require(min <= max) { "float range setting min must be <= max" }
		require(default == null || (default.isFinite() && default in min..max)) {
			"float range setting default must be finite and in range $min..$max"
		}
	}
}

data class StringSetting(
	override val id: SettingId,
	override val name: String,
	override val description: String = "",
	val default: String? = "",
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
	}
}

data class ColorSetting(
	override val id: SettingId,
	override val name: String,
	override val description: String = "",
	val default: Int? = 0xFFFFFF,
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
		require(default == null || default in RGB_COLOR_RANGE) { "color setting default must be in 0x000000..0xFFFFFF" }
	}
}

data class KeybindSetting(
	override val id: SettingId,
	override val name: String,
	override val description: String = "",
	val default: Int? = -1,
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
		require(default == null || default >= -1) { "keybind setting default must be -1 or greater" }
	}
}

data class ListSetting(
	override val id: SettingId,
	override val name: String,
	val itemSchema: SettingValueSchema,
	override val description: String = "",
	val default: List<Any?>? = emptyList(),
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
		require(default == null || default.all { itemSchema.accepts(it) }) { "list setting default must match item schema" }
	}
}

data class ObjectSetting(
	override val id: SettingId,
	override val name: String,
	val fields: List<SettingSchema>,
	override val description: String = "",
) : SettingSchema {
	init {
		requireNotBlank("setting name", name)
		requireUnique("object setting fields", fields.map { it.id })
	}
}

sealed interface SettingValueSchema {
	fun accepts(value: Any?): Boolean
}

data class BooleanValueSchema(val default: Boolean? = false) : SettingValueSchema {
	override fun accepts(value: Any?): Boolean = value is Boolean
}

data class EnumValueSchema(
	val values: List<String>,
	val default: String? = values.firstOrNull(),
) : SettingValueSchema {
	init {
		require(values.isNotEmpty()) { "enum value schema values must not be empty" }
		requireAllNotBlank("enum value schema values", values)
		requireUnique("enum value schema values", values)
		require(default == null || default in values) { "enum value schema default must be one of values" }
	}

	override fun accepts(value: Any?): Boolean = value is String && value in values
}

data class IntRangeValueSchema(
	val min: Int,
	val max: Int,
	val default: Int? = min,
) : SettingValueSchema {
	init {
		require(min <= max) { "int range value schema min must be <= max" }
		require(default == null || default in min..max) { "int range value schema default must be in range $min..$max" }
	}

	override fun accepts(value: Any?): Boolean = value is Int && value in min..max
}

data class FloatRangeValueSchema(
	val min: Double,
	val max: Double,
	val default: Double? = min,
) : SettingValueSchema {
	init {
		require(min.isFinite()) { "float range value schema min must be finite" }
		require(max.isFinite()) { "float range value schema max must be finite" }
		require(min <= max) { "float range value schema min must be <= max" }
		require(default == null || (default.isFinite() && default in min..max)) {
			"float range value schema default must be finite and in range $min..$max"
		}
	}

	override fun accepts(value: Any?): Boolean = value is Double && value.isFinite() && value in min..max
}

data class StringValueSchema(val default: String? = "") : SettingValueSchema {
	override fun accepts(value: Any?): Boolean = value is String
}

data class ColorValueSchema(val default: Int? = 0xFFFFFF) : SettingValueSchema {
	init {
		require(default == null || default in RGB_COLOR_RANGE) { "color value schema default must be in 0x000000..0xFFFFFF" }
	}

	override fun accepts(value: Any?): Boolean = value is Int && value in RGB_COLOR_RANGE
}

data class KeybindValueSchema(val default: Int? = -1) : SettingValueSchema {
	init {
		require(default == null || default >= -1) { "keybind value schema default must be -1 or greater" }
	}

	override fun accepts(value: Any?): Boolean = value is Int && value >= -1
}

data class ListValueSchema(
	val itemSchema: SettingValueSchema,
	val default: List<Any?>? = emptyList(),
) : SettingValueSchema {
	init {
		require(default == null || default.all { itemSchema.accepts(it) }) { "list value schema default must match item schema" }
	}

	override fun accepts(value: Any?): Boolean = value is List<*> && value.all { itemSchema.accepts(it) }
}

data class ObjectValueSchema(
	val fields: List<SettingSchema>,
) : SettingValueSchema {
	init {
		requireUnique("object value schema fields", fields.map { it.id })
	}

	override fun accepts(value: Any?): Boolean = value is Map<*, *>
}

data class EventSubscription(
	val eventId: String,
	val description: String = "",
) {
	init {
		requireNotBlank("eventId", eventId)
	}
}

data class HudWidgetSpec(
	val id: WidgetId,
	val name: String,
	val description: String = "",
) {
	init {
		requireNotBlank("HUD widget name", name)
	}
}

// Platform-agnostic keybind descriptor. The default key is a GLFW key code; -1 means unbound.
data class KeybindSpec(
	val id: KeybindId,
	val displayName: String,
	val defaultKey: Int = -1,
) {
	init {
		requireNotBlank("keybind displayName", displayName)
	}
}

data class CommandSpec(
	val path: String,
	val description: String = "",
) {
	init {
		requireNotBlank("command path", path)
	}
}

private fun requireNotBlank(label: String, value: String) {
	require(value.isNotBlank()) { "$label must not be blank" }
}

private fun requireAllNotBlank(label: String, values: List<String>) {
	require(values.all { it.isNotBlank() }) { "$label must not contain blank values" }
}

private fun <T> requireUnique(label: String, values: List<T>) {
	require(values.distinct().size == values.size) { "$label must not contain duplicates" }
}
