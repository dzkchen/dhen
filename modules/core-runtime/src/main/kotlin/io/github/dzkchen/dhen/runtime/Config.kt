package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.BooleanValueSchema
import io.github.dzkchen.dhen.api.ColorSetting
import io.github.dzkchen.dhen.api.ColorValueSchema
import io.github.dzkchen.dhen.api.EnumSetting
import io.github.dzkchen.dhen.api.EnumValueSchema
import io.github.dzkchen.dhen.api.FloatRangeSetting
import io.github.dzkchen.dhen.api.FloatRangeValueSchema
import io.github.dzkchen.dhen.api.IntRangeSetting
import io.github.dzkchen.dhen.api.IntRangeValueSchema
import io.github.dzkchen.dhen.api.KeybindSetting
import io.github.dzkchen.dhen.api.KeybindValueSchema
import io.github.dzkchen.dhen.api.ListSetting
import io.github.dzkchen.dhen.api.ListValueSchema
import io.github.dzkchen.dhen.api.ObjectSetting
import io.github.dzkchen.dhen.api.ObjectValueSchema
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.SettingSchema
import io.github.dzkchen.dhen.api.SettingValueSchema
import io.github.dzkchen.dhen.api.StringSetting
import io.github.dzkchen.dhen.api.StringValueSchema
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val CONFIG_SCHEMA_VERSION = 1

data class InstalledAddonState(
	val addonId: String,
	val version: String = "",
	val artifactType: String = "",
	val source: String = "",
	val checksum: String = "",
	val signatureStatus: String = "",
	val installedAtEpochMillis: Long? = null,
	val unknownFields: Map<String, Any?> = emptyMap(),
)

data class PendingRestartAddonState(
	val addonId: String,
	val operation: String = "",
	val version: String = "",
	val artifactPath: String = "",
	val unknownFields: Map<String, Any?> = emptyMap(),
)

data class HudLayoutState(
	val x: Int = 0,
	val y: Int = 0,
	val enabled: Boolean = true,
	val unknownFields: Map<String, Any?> = emptyMap(),
)

data class CoreConfigState(
	val schemaVersion: Int = CONFIG_SCHEMA_VERSION,
	val enabledModules: Set<String> = emptySet(),
	val enabledAddons: Set<String> = emptySet(),
	val installedAddons: Map<String, InstalledAddonState> = emptyMap(),
	val pendingRestartAddons: Map<String, PendingRestartAddonState> = emptyMap(),
	val hudLayout: Map<String, HudLayoutState> = emptyMap(),
	val keybinds: Map<String, Int> = emptyMap(),
	val conflictPreferences: Map<String, String> = emptyMap(),
	val unknownFields: Map<String, Any?> = emptyMap(),
)

data class ConfigAliases(
	val moduleAliases: Map<String, String> = emptyMap(),
	val settingAliases: Map<String, Map<String, String>> = emptyMap(),
)

private data class AddonConfigState(
	val schemaVersion: Int = CONFIG_SCHEMA_VERSION,
	val settings: MutableMap<String, Any?> = linkedMapOf(),
	val unknownFields: Map<String, Any?> = emptyMap(),
)

class ConfigStore(
	private val configDir: Path,
	private val codec: JsonCodec,
	private val aliases: ConfigAliases = ConfigAliases(),
) {
	val root: Path = configDir.resolve("dhen")
	val coreFile: Path = root.resolve("core.json")
	val addonsDir: Path = root.resolve("addons")

	fun ensureLayout(addonIds: Iterable<AddonId> = emptyList()) {
		Files.createDirectories(addonsDir)
		if (!Files.exists(coreFile)) saveCoreState(CoreConfigState())
		for (addonId in addonIds) ensureAddonFile(addonId)
	}

	fun ensureAddonFile(addonId: AddonId) {
		val path = addonFile(addonId)
		if (!Files.exists(path)) saveAddonState(addonId, AddonConfigState())
	}

	fun loadCoreState(): CoreConfigState {
		val parsed = (readJson(coreFile) as? Map<*, *>)?.toMutableStringMap() ?: return CoreConfigState()
		val migrated = migrateCore(parsed)
		val state = coreStateFromMap(migrated)
		if (migrated != parsed) saveCoreState(state)
		return state
	}

	fun saveCoreState(state: CoreConfigState) {
		writeJson(coreFile, state.toJsonMap())
	}

	fun updateCoreState(transform: (CoreConfigState) -> CoreConfigState): CoreConfigState {
		val updated = transform(loadCoreState())
		saveCoreState(updated)
		return updated
	}

	fun loadEnabledModules(): Set<String> {
		return loadCoreState().enabledModules
	}

	fun saveEnabledModules(ids: Set<String>) {
		updateCoreState { it.copy(enabledModules = ids.toSortedSet()) }
	}

	fun saveEnabledAddons(ids: Set<String>) {
		updateCoreState { it.copy(enabledAddons = ids.toSortedSet()) }
	}

	fun saveEnabledState(moduleIds: Set<String>, addonIds: Set<String>) {
		updateCoreState { it.copy(enabledModules = moduleIds.toSortedSet(), enabledAddons = addonIds.toSortedSet()) }
	}

	fun saveInstalledAddons(addons: Map<String, InstalledAddonState>) {
		updateCoreState { it.copy(installedAddons = addons.toSortedMap()) }
	}

	fun savePendingRestartAddons(addons: Map<String, PendingRestartAddonState>) {
		updateCoreState { it.copy(pendingRestartAddons = addons.toSortedMap()) }
	}

	fun saveHudLayout(layout: Map<String, HudLayoutState>) {
		updateCoreState { it.copy(hudLayout = layout.toSortedMap()) }
	}

	fun saveKeybinds(keybinds: Map<String, Int>) {
		updateCoreState { it.copy(keybinds = keybinds.toSortedMap()) }
	}

	fun saveConflictPreferences(preferences: Map<String, String>) {
		updateCoreState { it.copy(conflictPreferences = preferences.toSortedMap()) }
	}

	fun loadAddonSettings(addonId: AddonId): MutableMap<String, Any?> {
		return loadAddonState(addonId).settings
	}

	fun saveAddonSettings(addonId: AddonId, values: Map<String, Any?>) {
		val current = loadAddonState(addonId)
		saveAddonState(addonId, current.copy(settings = values.toMutableStringMap()))
	}

	private fun addonFile(addonId: AddonId): Path = addonsDir.resolve("${addonId.value}.json")

	private fun loadAddonState(addonId: AddonId): AddonConfigState {
		val parsed = (readJson(addonFile(addonId)) as? Map<*, *>)?.toMutableStringMap() ?: return AddonConfigState()
		val migrated = migrateAddon(addonId, parsed)
		val state = addonStateFromMap(migrated)
		if (migrated != parsed) saveAddonState(addonId, state)
		return state
	}

	private fun saveAddonState(addonId: AddonId, state: AddonConfigState) {
		writeJson(addonFile(addonId), state.toJsonMap())
	}

	private fun migrateCore(raw: MutableMap<String, Any?>): MutableMap<String, Any?> {
		val migrated = LinkedHashMap(raw)
		runMigrations(migrated, CORE_MIGRATIONS)
		applyModuleAliases(migrated)
		return migrated
	}

	private fun migrateAddon(addonId: AddonId, raw: MutableMap<String, Any?>): MutableMap<String, Any?> {
		val migrated = LinkedHashMap(raw)
		runMigrations(migrated, ADDON_MIGRATIONS)
		val settings = (migrated["settings"] as? Map<*, *>)?.toMutableStringMap() ?: linkedMapOf()
		applySettingAliases(settings, aliases.settingAliases[addonId.value].orEmpty())
		migrated["settings"] = settings
		return migrated
	}

	private fun runMigrations(raw: MutableMap<String, Any?>, migrations: List<ConfigMigration>) {
		var version = raw["schemaVersion"].asIntOrNull() ?: 0
		for (migration in migrations) {
			if (version >= migration.targetVersion) continue
			migration.apply(raw)
			version = migration.targetVersion
			raw["schemaVersion"] = version
		}
	}

	private fun applyModuleAliases(core: MutableMap<String, Any?>) {
		if (aliases.moduleAliases.isEmpty()) return
		core["enabledModules"] = aliasStringList(core["enabledModules"])
		core["hudLayout"] = aliasPlatformModuleMapKeys(core["hudLayout"])
		core["keybinds"] = aliasPlatformModuleMapKeys(core["keybinds"])
		core["conflictPreferences"] = aliasMapKeysAndStringValues(core["conflictPreferences"])
	}

	private fun aliasStringList(value: Any?): List<String> {
		val out = LinkedHashSet<String>()
		for (id in value.stringList()) out.add(aliases.moduleAliases[id] ?: id)
		return out.sorted()
	}

	private fun aliasMapKeysAndStringValues(value: Any?): Map<String, Any?> {
		val map = (value as? Map<*, *>)?.toMutableStringMap() ?: return emptyMap()
		val out = LinkedHashMap<String, Any?>()
		copyDestinationModuleKeys(map, out, ::aliasModuleStringValue)
		copyAliasedModuleKeys(map, out, ::aliasModuleStringValue)
		return out
	}

	private fun aliasPlatformModuleMapKeys(value: Any?): Map<String, Any?> {
		val map = (value as? Map<*, *>)?.toMutableStringMap() ?: return emptyMap()
		val out = LinkedHashMap<String, Any?>()
		copyDestinationPlatformModuleKeys(map, out)
		copyAliasedPlatformModuleKeys(map, out)
		return out
	}

	private fun copyDestinationModuleKeys(
		source: Map<String, Any?>,
		destination: MutableMap<String, Any?>,
		transformValue: (Any?) -> Any?,
	) {
		val aliasDestinations = aliases.moduleAliases.values.toSet()
		for ((key, item) in source) {
			if (key in aliasDestinations || key !in aliases.moduleAliases) destination[key] = transformValue(item)
		}
	}

	private fun copyAliasedModuleKeys(
		source: Map<String, Any?>,
		destination: MutableMap<String, Any?>,
		transformValue: (Any?) -> Any?,
	) {
		for ((key, item) in source) {
			val aliasedKey = aliases.moduleAliases[key] ?: continue
			destination.putIfAbsent(aliasedKey, transformValue(item))
		}
	}

	private fun copyDestinationPlatformModuleKeys(
		source: Map<String, Any?>,
		destination: MutableMap<String, Any?>,
	) {
		val aliasDestinations = aliases.moduleAliases.values.toSet()
		for ((key, item) in source) {
			val moduleId = key.substringBefore('/')
			if (moduleId in aliasDestinations || moduleId !in aliases.moduleAliases) destination[key] = item
		}
	}

	private fun copyAliasedPlatformModuleKeys(
		source: Map<String, Any?>,
		destination: MutableMap<String, Any?>,
	) {
		for ((key, item) in source) {
			val moduleId = key.substringBefore('/')
			val aliasedModuleId = aliases.moduleAliases[moduleId] ?: continue
			val aliasedKey = if ('/' in key) "$aliasedModuleId/${key.substringAfter('/')}" else aliasedModuleId
			destination.putIfAbsent(aliasedKey, item)
		}
	}

	private fun aliasModuleStringValue(value: Any?): Any? =
		(value as? String)?.let { aliases.moduleAliases[it] ?: it } ?: value

	private fun applySettingAliases(settings: MutableMap<String, Any?>, settingAliases: Map<String, String>) {
		for ((from, to) in settingAliases) movePath(settings, from.split('.'), to.split('.'))
	}

	private fun movePath(root: MutableMap<String, Any?>, from: List<String>, to: List<String>) {
		val value = removePath(root, from) ?: return
		if (!containsPath(root, to)) putPath(root, to, value)
	}

	private fun removePath(root: MutableMap<String, Any?>, path: List<String>): Any? {
		if (path.isEmpty()) return null
		val parent = path.dropLast(1).fold(root as MutableMap<String, Any?>?) { current, segment ->
			(current?.get(segment) as? Map<*, *>)?.toMutableStringMap()?.also { current[segment] = it }
		} ?: return null
		return parent.remove(path.last())
	}

	private fun containsPath(root: Map<String, Any?>, path: List<String>): Boolean {
		if (path.isEmpty()) return false
		var current: Any? = root
		for (segment in path.dropLast(1)) current = (current as? Map<*, *>)?.get(segment) ?: return false
		return (current as? Map<*, *>)?.containsKey(path.last()) == true
	}

	private fun putPath(root: MutableMap<String, Any?>, path: List<String>, value: Any?) {
		var current = root
		for (segment in path.dropLast(1)) {
			val next = (current[segment] as? Map<*, *>)?.toMutableStringMap() ?: linkedMapOf()
			current[segment] = next
			current = next
		}
		current[path.last()] = value
	}

	private fun readJson(path: Path): Any? {
		if (!Files.exists(path)) return null
		return try {
			codec.decode(Files.readString(path))
		} catch (t: Throwable) {
			null
		}
	}

	private fun writeJson(path: Path, value: Any?) {
		val text = codec.encode(value)
		codec.decode(text)
		Files.createDirectories(path.parent)
		val tmp = path.resolveSibling("${path.fileName}.tmp")
		Files.writeString(tmp, text)
		try {
			Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
		} catch (_: Throwable) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
		}
	}

	private fun coreStateFromMap(map: Map<String, Any?>): CoreConfigState = CoreConfigState(
		schemaVersion = map["schemaVersion"].asIntOrNull() ?: CONFIG_SCHEMA_VERSION,
		enabledModules = map["enabledModules"].stringList().toSortedSet(),
		enabledAddons = map["enabledAddons"].stringList().toSortedSet(),
		installedAddons = stateMap(map["installedAddons"], ::installedAddonFromMap),
		pendingRestartAddons = stateMap(map["pendingRestartAddons"], ::pendingRestartAddonFromMap),
		hudLayout = stateMap(map["hudLayout"], ::hudLayoutFromMap),
		keybinds = intMap(map["keybinds"]),
		conflictPreferences = stringMap(map["conflictPreferences"]),
		unknownFields = map.filterKeys { it !in CORE_FIELDS },
	)

	private fun addonStateFromMap(map: Map<String, Any?>): AddonConfigState = AddonConfigState(
		schemaVersion = map["schemaVersion"].asIntOrNull() ?: CONFIG_SCHEMA_VERSION,
		settings = (map["settings"] as? Map<*, *>)?.toMutableStringMap() ?: linkedMapOf(),
		unknownFields = map.filterKeys { it !in ADDON_FIELDS },
	)

	private fun installedAddonFromMap(id: String, map: Map<String, Any?>): InstalledAddonState = InstalledAddonState(
		addonId = stringValue(map["addonId"]) ?: id,
		version = stringValue(map["version"]).orEmpty(),
		artifactType = stringValue(map["artifactType"]).orEmpty(),
		source = stringValue(map["source"]).orEmpty(),
		checksum = stringValue(map["checksum"]).orEmpty(),
		signatureStatus = stringValue(map["signatureStatus"]).orEmpty(),
		installedAtEpochMillis = map["installedAtEpochMillis"].asLongOrNull(),
		unknownFields = map.filterKeys { it !in INSTALLED_ADDON_FIELDS },
	)

	private fun pendingRestartAddonFromMap(id: String, map: Map<String, Any?>): PendingRestartAddonState = PendingRestartAddonState(
		addonId = stringValue(map["addonId"]) ?: id,
		operation = stringValue(map["operation"]).orEmpty(),
		version = stringValue(map["version"]).orEmpty(),
		artifactPath = stringValue(map["artifactPath"]).orEmpty(),
		unknownFields = map.filterKeys { it !in PENDING_RESTART_FIELDS },
	)

	private fun hudLayoutFromMap(id: String, map: Map<String, Any?>): HudLayoutState = HudLayoutState(
		x = map["x"].asIntOrNull() ?: 0,
		y = map["y"].asIntOrNull() ?: 0,
		enabled = map["enabled"] as? Boolean ?: true,
		unknownFields = map.filterKeys { it !in HUD_LAYOUT_FIELDS },
	)

	private fun <T> stateMap(value: Any?, parser: (String, Map<String, Any?>) -> T): Map<String, T> {
		val map = value as? Map<*, *> ?: return emptyMap()
		val out = LinkedHashMap<String, T>()
		for ((key, item) in map) {
			if (key is String && item is Map<*, *>) out[key] = parser(key, item.toMutableStringMap())
		}
		return out
	}

	private fun intMap(value: Any?): Map<String, Int> {
		val map = value as? Map<*, *> ?: return emptyMap()
		val out = LinkedHashMap<String, Int>()
		for ((key, item) in map) {
			val intValue = item.asIntOrNull()
			if (key is String && intValue != null) out[key] = intValue
		}
		return out
	}

	private fun stringMap(value: Any?): Map<String, String> {
		val map = value as? Map<*, *> ?: return emptyMap()
		val out = LinkedHashMap<String, String>()
		for ((key, item) in map) if (key is String && item is String) out[key] = item
		return out
	}

	private fun CoreConfigState.toJsonMap(): Map<String, Any?> {
		val out = LinkedHashMap<String, Any?>(unknownFields)
		out["schemaVersion"] = stateSchemaVersion(schemaVersion)
		out["enabledModules"] = enabledModules.sorted()
		out["enabledAddons"] = enabledAddons.sorted()
		out["installedAddons"] = installedAddons.toSortedMap().mapValues { it.value.toJsonMap() }
		out["pendingRestartAddons"] = pendingRestartAddons.toSortedMap().mapValues { it.value.toJsonMap() }
		out["hudLayout"] = hudLayout.toSortedMap().mapValues { it.value.toJsonMap() }
		out["keybinds"] = keybinds.toSortedMap()
		out["conflictPreferences"] = conflictPreferences.toSortedMap()
		return out
	}

	private fun AddonConfigState.toJsonMap(): Map<String, Any?> {
		val out = LinkedHashMap<String, Any?>(unknownFields)
		out["schemaVersion"] = stateSchemaVersion(schemaVersion)
		out["settings"] = settings
		return out
	}

	private fun InstalledAddonState.toJsonMap(): Map<String, Any?> {
		val out = LinkedHashMap<String, Any?>(unknownFields)
		out["addonId"] = addonId
		out["version"] = version
		out["artifactType"] = artifactType
		out["source"] = source
		out["checksum"] = checksum
		out["signatureStatus"] = signatureStatus
		if (installedAtEpochMillis != null) out["installedAtEpochMillis"] = installedAtEpochMillis
		return out
	}

	private fun PendingRestartAddonState.toJsonMap(): Map<String, Any?> {
		val out = LinkedHashMap<String, Any?>(unknownFields)
		out["addonId"] = addonId
		out["operation"] = operation
		out["version"] = version
		out["artifactPath"] = artifactPath
		return out
	}

	private fun HudLayoutState.toJsonMap(): Map<String, Any?> {
		val out = LinkedHashMap<String, Any?>(unknownFields)
		out["x"] = x
		out["y"] = y
		out["enabled"] = enabled
		return out
	}

	private companion object {
		val CORE_MIGRATIONS = listOf(
			ConfigMigration(1) { migrated ->
				migrated.putIfAbsent("enabledModules", emptyList<String>())
				migrated.putIfAbsent("enabledAddons", emptyList<String>())
				migrated.putIfAbsent("installedAddons", emptyMap<String, Any?>())
				migrated.putIfAbsent("pendingRestartAddons", emptyMap<String, Any?>())
				migrated.putIfAbsent("hudLayout", emptyMap<String, Any?>())
				migrated.putIfAbsent("keybinds", emptyMap<String, Any?>())
				migrated.putIfAbsent("conflictPreferences", emptyMap<String, Any?>())
			},
		)
		val ADDON_MIGRATIONS = listOf(
			ConfigMigration(1) { migrated ->
				val alreadyEnveloped = migrated["settings"] is Map<*, *> &&
					migrated.keys.all { it == "settings" || it == "schemaVersion" }
				if (!alreadyEnveloped) {
					val settings = LinkedHashMap<String, Any?>()
					for ((key, value) in migrated) if (key != "schemaVersion") settings[key] = value
					migrated.clear()
					migrated["settings"] = settings
				}
			},
		)
		val CORE_FIELDS = setOf(
			"schemaVersion",
			"enabledModules",
			"enabledAddons",
			"installedAddons",
			"pendingRestartAddons",
			"hudLayout",
			"keybinds",
			"conflictPreferences",
		)
		val ADDON_FIELDS = setOf("schemaVersion", "settings")
		val INSTALLED_ADDON_FIELDS = setOf(
			"addonId",
			"version",
			"artifactType",
			"source",
			"checksum",
			"signatureStatus",
			"installedAtEpochMillis",
		)
		val PENDING_RESTART_FIELDS = setOf("addonId", "operation", "version", "artifactPath")
		val HUD_LAYOUT_FIELDS = setOf("x", "y", "enabled")
	}
}

private data class ConfigMigration(
	val targetVersion: Int,
	val apply: (MutableMap<String, Any?>) -> Unit,
)

class ConfigManager(private val store: ConfigStore) {
	private val valuesByAddon = LinkedHashMap<AddonId, MutableMap<String, Any?>>()
	private val schemasByAddon = LinkedHashMap<AddonId, Map<SettingId, SettingSchema>>()

	fun materializeDefaults(addonId: AddonId, schemas: List<SettingSchema>): List<String> {
		val duplicateIds = duplicateSettingIds(schemas)
		val uniqueSchemas = schemas.filter { it.id !in duplicateIds }
		schemasByAddon[addonId] = uniqueSchemas.associateBy { it.id }
		val current = valuesByAddon.getOrPut(addonId) { store.loadAddonSettings(addonId) }
		var changed = false
		for (schema in uniqueSchemas) changed = materializeSettingDefaults(current, schema) || changed
		if (changed) store.saveAddonSettings(addonId, current)
		return duplicateIds.map { "${addonId.value}.${it.value}: duplicate setting id" } +
			ConfigValidator.validate(addonId, current, uniqueSchemas)
	}

	fun getBoolean(addonId: AddonId, settingId: SettingId): Boolean {
		val v = valuesByAddon[addonId]?.get(settingId.value)
		return when (v) {
			is Boolean -> v
			else -> {
				val default = (schemasByAddon[addonId]?.get(settingId) as? BooleanSetting)?.default
				default ?: throw IllegalStateException(
					"Required boolean setting ${addonId.value}.${settingId.value} is missing or invalid",
				)
			}
		}
	}

	private fun defaultOf(schema: SettingSchema): Any? = when (schema) {
		is BooleanSetting -> schema.default
		is ColorSetting -> schema.default
		is EnumSetting -> schema.default
		is FloatRangeSetting -> schema.default
		is IntRangeSetting -> schema.default
		is KeybindSetting -> schema.default
		is ListSetting -> schema.default?.map { materializeDefaultValue(it, schema.itemSchema) }
		is ObjectSetting -> objectDefault(schema.fields)
		is StringSetting -> schema.default
	}

	private fun defaultOf(schema: SettingValueSchema): Any? = when (schema) {
		is BooleanValueSchema -> schema.default
		is ColorValueSchema -> schema.default
		is EnumValueSchema -> schema.default
		is FloatRangeValueSchema -> schema.default
		is IntRangeValueSchema -> schema.default
		is KeybindValueSchema -> schema.default
		is ListValueSchema -> schema.default?.map { materializeDefaultValue(it, schema.itemSchema) }
		is ObjectValueSchema -> objectDefault(schema.fields)
		is StringValueSchema -> schema.default
	}

	private fun objectDefault(fields: List<SettingSchema>): Map<String, Any?>? {
		val out = LinkedHashMap<String, Any?>()
		for (field in fields) {
			val default = defaultOf(field)
			if (default != null) out[field.id.value] = default
		}
		return out.ifEmpty { null }
	}

	private fun materializeDefaultValue(value: Any?, schema: SettingValueSchema): Any? = when (schema) {
		is ListValueSchema -> (value as? List<*>)?.map { materializeDefaultValue(it, schema.itemSchema) } ?: value
		is ObjectValueSchema -> {
			val objectValue = (value as? Map<*, *>)?.toMutableStringMap() ?: return value
			materializeObjectDefaults(objectValue, schema.fields)
			objectValue
		}
		else -> value
	}

	private fun materializeSettingDefaults(values: MutableMap<String, Any?>, schema: SettingSchema): Boolean {
		if (!values.containsKey(schema.id.value)) {
			val default = defaultOf(schema) ?: return false
			values[schema.id.value] = default
			return true
		}
		return when (schema) {
			is ListSetting -> {
				val listValue = (values[schema.id.value] as? List<*>)?.toMutableAnyList() ?: return false
				values[schema.id.value] = listValue
				materializeListDefaults(listValue, schema.itemSchema)
			}
			is ObjectSetting -> {
				val objectValue = (values[schema.id.value] as? Map<*, *>)?.toMutableStringMap() ?: return false
				values[schema.id.value] = objectValue
				materializeObjectDefaults(objectValue, schema.fields)
			}
			else -> false
		}
	}

	private fun materializeObjectDefaults(values: MutableMap<String, Any?>, fields: List<SettingSchema>): Boolean {
		var changed = false
		for (field in fields) {
			changed = materializeSettingDefaults(values, field) || changed
		}
		return changed
	}

	private fun materializeListDefaults(values: MutableList<Any?>, itemSchema: SettingValueSchema): Boolean {
		var changed = false
		for (index in values.indices) {
			when (itemSchema) {
				is ListValueSchema -> {
					val listValue = (values[index] as? List<*>)?.toMutableAnyList() ?: continue
					values[index] = listValue
					changed = materializeListDefaults(listValue, itemSchema.itemSchema) || changed
				}
				is ObjectValueSchema -> {
					val objectValue = (values[index] as? Map<*, *>)?.toMutableStringMap() ?: continue
					values[index] = objectValue
					changed = materializeObjectDefaults(objectValue, itemSchema.fields) || changed
				}
				else -> Unit
			}
		}
		return changed
	}

	private fun duplicateSettingIds(schemas: List<SettingSchema>): Set<SettingId> =
		schemas.groupBy { it.id }.filterValues { it.size > 1 }.keys
}

object ConfigValidator {
	fun validate(addonId: AddonId, values: Map<String, Any?>, schemas: List<SettingSchema>): List<String> =
		schemas.flatMap { validateSetting("${addonId.value}.${it.id.value}", values[it.id.value], values.containsKey(it.id.value), it) }

	private fun validateSetting(path: String, value: Any?, present: Boolean, schema: SettingSchema): List<String> {
		if (!present) {
			if (schema is ObjectSetting) return validateObject(path, emptyMap<String, Any?>(), schema.fields)
			return if (defaultOf(schema) == null) listOf("$path: required setting is missing") else emptyList()
		}
		return when (schema) {
			is BooleanSetting -> expect(value is Boolean, path, "expected boolean")
			is ColorSetting -> validateInt(value, path, "expected RGB integer in range 0..16777215") { it in 0..0xFFFFFF }
			is EnumSetting -> expect(value is String && value in schema.values, path, "expected one of ${schema.values.joinToString()}")
			is FloatRangeSetting -> validateFloat(value, path, "expected finite number in range ${schema.min}..${schema.max}") {
				it in schema.min..schema.max
			}
			is IntRangeSetting -> validateInt(value, path, "expected integer in range ${schema.min}..${schema.max}") {
				it in schema.min..schema.max
			}
			is KeybindSetting -> validateInt(value, path, "expected key code integer >= -1") { it >= -1 }
			is ListSetting -> validateList(path, value, schema.itemSchema)
			is ObjectSetting -> validateObject(path, value, schema.fields)
			is StringSetting -> expect(value is String, path, "expected string")
		}
	}

	private fun validateValue(path: String, value: Any?, schema: SettingValueSchema): List<String> {
		if (value == null && defaultOf(schema) == null) return listOf("$path: required value is missing")
		return when (schema) {
			is BooleanValueSchema -> expect(value is Boolean, path, "expected boolean")
			is ColorValueSchema -> validateInt(value, path, "expected RGB integer in range 0..16777215") { it in 0..0xFFFFFF }
			is EnumValueSchema -> expect(value is String && value in schema.values, path, "expected one of ${schema.values.joinToString()}")
			is FloatRangeValueSchema -> validateFloat(value, path, "expected finite number in range ${schema.min}..${schema.max}") {
				it in schema.min..schema.max
			}
			is IntRangeValueSchema -> validateInt(value, path, "expected integer in range ${schema.min}..${schema.max}") {
				it in schema.min..schema.max
			}
			is KeybindValueSchema -> validateInt(value, path, "expected key code integer >= -1") { it >= -1 }
			is ListValueSchema -> validateList(path, value, schema.itemSchema)
			is ObjectValueSchema -> validateObject(path, value, schema.fields)
			is StringValueSchema -> expect(value is String, path, "expected string")
		}
	}

	private fun validateList(path: String, value: Any?, itemSchema: SettingValueSchema): List<String> {
		val list = value as? List<*> ?: return listOf("$path: expected list")
		return list.flatMapIndexed { index, item -> validateValue("$path[$index]", item, itemSchema) }
	}

	private fun validateObject(path: String, value: Any?, fields: List<SettingSchema>): List<String> {
		val map = value as? Map<*, *> ?: return listOf("$path: expected object")
		return fields.flatMap { field ->
			validateSetting("$path.${field.id.value}", map[field.id.value], map.containsKey(field.id.value), field)
		}
	}

	private fun validateInt(value: Any?, path: String, message: String, predicate: (Int) -> Boolean): List<String> {
		val intValue = value.asIntOrNull() ?: return listOf("$path: $message")
		return expect(predicate(intValue), path, message)
	}

	private fun validateFloat(value: Any?, path: String, message: String, predicate: (Double) -> Boolean): List<String> {
		val doubleValue = (value as? Number)?.toDouble()
		if (doubleValue == null || !doubleValue.isFinite()) return listOf("$path: $message")
		return expect(predicate(doubleValue), path, message)
	}

	private fun expect(condition: Boolean, path: String, message: String): List<String> =
		if (condition) emptyList() else listOf("$path: $message")

	private fun defaultOf(schema: SettingSchema): Any? = when (schema) {
		is BooleanSetting -> schema.default
		is ColorSetting -> schema.default
		is EnumSetting -> schema.default
		is FloatRangeSetting -> schema.default
		is IntRangeSetting -> schema.default
		is KeybindSetting -> schema.default
		is ListSetting -> schema.default
		is ObjectSetting -> objectDefault(schema.fields)
		is StringSetting -> schema.default
	}

	private fun defaultOf(schema: SettingValueSchema): Any? = when (schema) {
		is BooleanValueSchema -> schema.default
		is ColorValueSchema -> schema.default
		is EnumValueSchema -> schema.default
		is FloatRangeValueSchema -> schema.default
		is IntRangeValueSchema -> schema.default
		is KeybindValueSchema -> schema.default
		is ListValueSchema -> schema.default
		is ObjectValueSchema -> objectDefault(schema.fields)
		is StringValueSchema -> schema.default
	}

	private fun objectDefault(fields: List<SettingSchema>): Map<String, Any?>? {
		val out = LinkedHashMap<String, Any?>()
		for (field in fields) {
			val default = defaultOf(field)
			if (default != null) out[field.id.value] = default
		}
		return out.ifEmpty { null }
	}
}

private fun Map<*, *>.toMutableStringMap(): MutableMap<String, Any?> {
	val out = LinkedHashMap<String, Any?>()
	for ((key, value) in this) if (key is String) out[key] = value
	return out
}

private fun List<*>.toMutableAnyList(): MutableList<Any?> = ArrayList(this)

private fun Any?.stringList(): List<String> = (this as? List<*>)?.mapNotNull { it as? String }.orEmpty()

private fun stringValue(value: Any?): String? = value as? String

private fun stateSchemaVersion(version: Int): Int = version.coerceAtLeast(CONFIG_SCHEMA_VERSION)

private fun Any?.asIntOrNull(): Int? {
	val number = this as? Number ?: return null
	val double = number.toDouble()
	if (!double.isFinite() || double % 1.0 != 0.0 || double < Int.MIN_VALUE || double > Int.MAX_VALUE) return null
	return double.toInt()
}

private fun Any?.asLongOrNull(): Long? {
	val number = this as? Number ?: return null
	val double = number.toDouble()
	if (!double.isFinite() || double % 1.0 != 0.0 || double < Long.MIN_VALUE || double > Long.MAX_VALUE) return null
	return double.toLong()
}
