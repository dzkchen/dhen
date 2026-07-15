package io.github.dzkchen.dhen.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.Color
import org.slf4j.LoggerFactory

// Bridges a ModuleManager to the JSON document ConfigStore persists: enabled
// state plus persistable settings, dispatched on concrete Setting subtype.
// ActionSetting (a callback) is never serialized. Unknown modules/settings are
// skipped on load; the merge base keeps them in the file.
object ModulePersistence {
	internal val migrations: List<(JsonObject) -> Unit> = emptyList()
	internal val version: Int
		get() = migrations.size

	private val log = LoggerFactory.getLogger(ModulePersistence::class.java)

	fun snapshot(manager: ModuleManager): JsonObject {
		val modules = JsonObject()
		for (module in manager.modules) {
			val entry = JsonObject()
			entry.addProperty("enabled", module.enabled)
			val settings = JsonObject()
			for (setting in module.settings) serialize(setting)?.let { settings.add(setting.name, it) }
			entry.add("settings", settings)
			modules.add(module.name, entry)
		}
		return JsonObject().apply { add("modules", modules) }
	}

	fun apply(manager: ModuleManager, doc: JsonObject) {
		val modules = doc.get("modules") as? JsonObject ?: return
		for ((name, element) in modules.entrySet()) {
			val module = manager[name] ?: continue
			val entry = element as? JsonObject ?: continue
			(entry.get("enabled") as? JsonPrimitive)?.let {
				if (it.asBoolean) manager.enable(name) else manager.disable(name)
			}
			val settings = entry.get("settings") as? JsonObject ?: continue
			for (setting in module.settings) {
				val value = settings.get(setting.name) ?: continue
				try {
					deserialize(setting, value)
				} catch (e: Exception) {
					log.warn("Skipping bad value for '{}' in module '{}'", setting.name, name, e)
				}
			}
		}
	}

	private fun serialize(setting: Setting<*>): JsonElement? = when (setting) {
		is BooleanSetting -> JsonPrimitive(setting.value)
		is NumberSetting -> JsonPrimitive(setting.value)
		is ColorSetting -> JsonPrimitive(setting.value.argb)
		is KeybindSetting -> JsonPrimitive(setting.value)
		is SelectorSetting -> JsonPrimitive(setting.value)
		is StringSetting -> JsonPrimitive(setting.value)
		else -> null
	}

	private fun deserialize(setting: Setting<*>, element: JsonElement) {
		if (element !is JsonPrimitive) return
		when (setting) {
			is BooleanSetting -> setting.value = element.asBoolean
			is NumberSetting -> setting.value = element.asDouble
			is ColorSetting -> setting.value = Color(element.asInt)
			is KeybindSetting -> setting.value = element.asInt
			is SelectorSetting -> setting.value = element.asString
			is StringSetting -> setting.value = element.asString
			else -> {}
		}
	}
}
