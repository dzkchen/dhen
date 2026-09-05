package io.github.dzkchen.dhen.ui.hud

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.flagOrNull
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.textOrNull
import org.slf4j.LoggerFactory

object HudPersistence {
	private const val ANCHOR = "anchor"
	private const val OFFSET_X = "x"
	private const val OFFSET_Y = "y"
	private const val SCALE = "scale"
	private const val VISIBLE = "visible"
	private const val BACKGROUND = "background"
	private const val IN_MENUS = "menus"

	private val log = LoggerFactory.getLogger(HudPersistence::class.java)

	fun snapshot(elements: List<HudElement>): JsonObject {
		val hud = JsonObject()
		for (element in elements) {
			if (element.atDeclared) continue
			hud.add(element.name, JsonObject().apply {
				addProperty(ANCHOR, element.anchor.name)
				addProperty(OFFSET_X, element.offsetX)
				addProperty(OFFSET_Y, element.offsetY)
				addProperty(SCALE, element.scale)
				addProperty(VISIBLE, element.visible)
				addProperty(BACKGROUND, element.background)
				addProperty(IN_MENUS, element.inMenus)
			})
		}
		return hud
	}

	fun apply(elements: List<HudElement>, doc: JsonObject) {
		for (element in elements) {
			val entry = doc.obj(element.name) ?: continue
			read(entry, ANCHOR, element.name, ::anchor) { element.anchor = it }
			read(entry, OFFSET_X, element.name, { it.numberOrNull()?.toInt() }) { element.offsetX = it }
			read(entry, OFFSET_Y, element.name, { it.numberOrNull()?.toInt() }) { element.offsetY = it }
			read(entry, SCALE, element.name, { it.numberOrNull()?.toFloat() }) { element.scale = it }
			read(entry, VISIBLE, element.name, { it.flagOrNull() }) { element.visible = it }
			read(entry, BACKGROUND, element.name, { it.flagOrNull() }) { element.background = it }
			read(entry, IN_MENUS, element.name, { it.flagOrNull() }) { element.inMenus = it }
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

	private fun anchor(element: JsonElement): HudAnchor? {
		val name = element.textOrNull() ?: return null
		return HudAnchor.entries.firstOrNull { it.name == name }
	}
}
