package io.github.dzkchen.dhen.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.Color
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

internal object SettingCodec {
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	fun writeInto(block: JsonObject, settings: List<Setting<*>>): JsonObject {
		for (i in settings.indices) {
			val setting = settings[i]
			serialize(setting)?.let { block.add(setting.name, it) }
		}
		return block
	}

	fun readInto(block: JsonObject?, settings: List<Setting<*>>, owner: String) {
		if (block == null) return
		for (i in settings.indices) {
			val setting = settings[i]
			val value = block.get(setting.name) ?: continue
			try {
				deserialize(setting, value)
			} catch (e: Exception) {
				log.warn("Skipping bad value for '{}' in '{}'", setting.name, owner, e)
			}
		}
	}

	fun serialize(setting: Setting<*>): JsonElement? = if (!setting.isStored) null else when (setting) {
		is BooleanSetting -> JsonPrimitive(setting.value)
		is NumberSetting -> JsonPrimitive(setting.value)
		is ColorSetting -> JsonPrimitive(setting.value.argb)
		is KeybindSetting -> JsonPrimitive(setting.value)
		is SelectorSetting -> JsonPrimitive(setting.preferred)
		is OrderedSelectionSetting -> JsonArray().also { array -> setting.value.forEach(array::add) }
		is SoundSetting -> JsonPrimitive(setting.value.location().toString())
		is StringSetting -> JsonPrimitive(setting.value)
		else -> null
	}

	fun deserialize(setting: Setting<*>, element: JsonElement) {
		if (setting is OrderedSelectionSetting) {
			if (element !is JsonArray) return
			setting.value = element.mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }
			return
		}
		if (element !is JsonPrimitive) return
		when (setting) {
			is BooleanSetting -> setting.value = element.asBoolean
			is NumberSetting -> setting.value = element.asDouble
			is ColorSetting -> setting.value = Color(element.asInt)
			is KeybindSetting -> setting.value = element.asInt
			is SelectorSetting -> setting.value = element.asString
			is SoundSetting -> setting.select(Identifier.parse(element.asString))
			is StringSetting -> setting.value = element.asString
			else -> {}
		}
	}
}
