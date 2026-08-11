package io.github.dzkchen.dhen.ui.hud

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.slf4j.LoggerFactory

object HudPersistence {
	private const val ANCHOR = "anchor"
	private const val OFFSET_X = "x"
	private const val OFFSET_Y = "y"
	private const val SCALE = "scale"
	private const val VISIBLE = "visible"

	private val log = LoggerFactory.getLogger(HudPersistence::class.java)

	fun snapshot(elements: List<HudElement>): JsonObject {
		val hud = JsonObject()
		for (element in elements) {
			hud.add(element.name, JsonObject().apply {
				addProperty(ANCHOR, element.anchor.name)
				addProperty(OFFSET_X, element.offsetX)
				addProperty(OFFSET_Y, element.offsetY)
				addProperty(SCALE, element.scale)
				addProperty(VISIBLE, element.visible)
			})
		}
		return hud
	}

	fun apply(elements: List<HudElement>, doc: JsonObject) {
		for (element in elements) {
			val entry = doc.get(element.name) as? JsonObject ?: continue
			read(entry, ANCHOR, element.name, { it.asAnchorOrNull() }) { element.anchor = it }
			read(entry, OFFSET_X, element.name, { it.asIntOrNull() }) { element.offsetX = it }
			read(entry, OFFSET_Y, element.name, { it.asIntOrNull() }) { element.offsetY = it }
			read(entry, SCALE, element.name, { it.asFloatOrNull() }) { element.scale = it }
			read(entry, VISIBLE, element.name, { it.asBooleanOrNull() }) { element.visible = it }
		}
	}

	private inline fun <T : Any> read(
		entry: JsonObject,
		key: String,
		elementName: String,
		parse: (JsonElement) -> T?,
		apply: (T) -> Unit
	) {
		val value = entry.get(key) ?: return
		val parsed = try {
			parse(value)
		} catch (e: Exception) {
			log.warn("Skipping bad '{}' for HUD element '{}'", key, elementName, e)
			return
		}
		if (parsed == null) {
			log.warn("Skipping bad '{}' for HUD element '{}'", key, elementName)
			return
		}
		apply(parsed)
	}

	private fun JsonElement.asAnchorOrNull(): HudAnchor? {
		val name = (this as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: return null
		return HudAnchor.entries.firstOrNull { it.name == name }
	}

	private fun JsonElement.asIntOrNull(): Int? =
		(this as? JsonPrimitive)?.takeIf { it.isNumber }?.asInt

	private fun JsonElement.asFloatOrNull(): Float? =
		(this as? JsonPrimitive)?.takeIf { it.isNumber }?.asFloat

	private fun JsonElement.asBooleanOrNull(): Boolean? =
		(this as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean
}
