package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonId
import io.github.dzkchen.dhen.api.BooleanSetting
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.SettingSchema
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ConfigStore(
	private val configDir: Path,
	private val codec: JsonCodec,
) {
	private val root: Path = configDir.resolve("dhen")
	private val coreFile: Path = root.resolve("core.json")
	private val addonsDir: Path = root.resolve("addons")

	fun loadEnabledModules(): Set<String> {
		val parsed = readJson(coreFile) as? Map<*, *> ?: return emptySet()
		val list = parsed["enabledModules"] as? List<*> ?: return emptySet()
		return list.mapNotNull { it as? String }.toSet()
	}

	fun saveEnabledModules(ids: Set<String>) {
		writeJson(coreFile, mapOf("enabledModules" to ids.sorted()))
	}

	fun loadAddonSettings(addonId: AddonId): MutableMap<String, Any?> {
		val parsed = readJson(addonFile(addonId)) as? Map<*, *> ?: return mutableMapOf()
		val out = LinkedHashMap<String, Any?>()
		for ((k, v) in parsed) if (k is String) out[k] = v
		return out
	}

	fun saveAddonSettings(addonId: AddonId, values: Map<String, Any?>) {
		writeJson(addonFile(addonId), values)
	}

	private fun addonFile(addonId: AddonId): Path = addonsDir.resolve("${addonId.value}.json")

	private fun readJson(path: Path): Any? {
		if (!Files.exists(path)) return null
		return try {
			codec.decode(Files.readString(path))
		} catch (t: Throwable) {
			null
		}
	}

	private fun writeJson(path: Path, value: Any?) {
		Files.createDirectories(path.parent)
		val text = codec.encode(value)
		val tmp = path.resolveSibling("${path.fileName}.tmp")
		Files.writeString(tmp, text)
		try {
			Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
		} catch (_: Throwable) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
		}
	}
}

class ConfigManager(private val store: ConfigStore) {
	private val valuesByAddon = LinkedHashMap<AddonId, MutableMap<String, Any?>>()

	fun materializeDefaults(addonId: AddonId, schemas: List<SettingSchema>) {
		val current = valuesByAddon.getOrPut(addonId) { store.loadAddonSettings(addonId) }
		var changed = false
		for (schema in schemas) {
			if (!current.containsKey(schema.id.value)) {
				current[schema.id.value] = defaultOf(schema)
				changed = true
			}
		}
		if (changed) store.saveAddonSettings(addonId, current)
	}

	fun getBoolean(addonId: AddonId, settingId: SettingId): Boolean {
		val v = valuesByAddon[addonId]?.get(settingId.value)
		return when (v) {
			is Boolean -> v
			is Number -> v.toInt() != 0
			else -> false
		}
	}

	private fun defaultOf(schema: SettingSchema): Any? = when (schema) {
		is BooleanSetting -> schema.default
	}
}
