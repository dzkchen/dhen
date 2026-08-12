package io.github.dzkchen.dhen.gui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal object ClickGuiView {
	private const val CLICK_GUI = "clickgui"
	private const val COLLAPSED = "collapsed"

	fun read(doc: JsonObject): MutableSet<String> {
		val collapsed = linkedSetOf<String>()
		val block = doc.get(CLICK_GUI) as? JsonObject ?: return collapsed
		val names = block.get(COLLAPSED) as? JsonArray ?: return collapsed
		for (element in names) {
			val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: continue
			collapsed += name
		}
		return collapsed
	}

	fun writeInto(doc: JsonObject, collapsed: Set<String>): JsonObject {
		val names = JsonArray()
		for (name in collapsed) names.add(name)
		val block = doc.get(CLICK_GUI) as? JsonObject ?: JsonObject().also { doc.add(CLICK_GUI, it) }
		block.add(COLLAPSED, names)
		return doc
	}
}
