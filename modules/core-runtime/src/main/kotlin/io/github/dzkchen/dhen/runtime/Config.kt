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

private fun Any?.asIntOrNull(): Int? {
	val number = this as? Number ?: return null
	val double = number.toDouble()
	if (!double.isFinite() || double % 1.0 != 0.0 || double < Int.MIN_VALUE || double > Int.MAX_VALUE) return null
	return double.toInt()
}
