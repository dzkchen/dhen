package io.github.dzkchen.dhen.gui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal class ClickGuiState(val collapsed: MutableSet<String> = linkedSetOf()) {
	fun toggle(category: String) {
		if (!collapsed.remove(category)) collapsed.add(category)
	}

	fun isBodyHidden(category: String): Boolean = category in collapsed
}

internal object ClickGuiView {
	const val CLICK_GUI = "clickgui"

	private const val COLLAPSED = "collapsed"

	fun read(doc: JsonObject): ClickGuiState {
		val block = doc.get(CLICK_GUI) as? JsonObject ?: return ClickGuiState()
		return ClickGuiState(collapsedIn(block))
	}

	fun writeInto(doc: JsonObject, state: ClickGuiState): JsonObject {
		val block = doc.get(CLICK_GUI) as? JsonObject ?: JsonObject().also { doc.add(CLICK_GUI, it) }
		block.add(COLLAPSED, namesOf(state.collapsed))
		return doc
	}

	private fun collapsedIn(block: JsonObject): MutableSet<String> {
		val names = linkedSetOf<String>()
		val stored = block.get(COLLAPSED) as? JsonArray ?: return names
		for (element in stored) {
			val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: continue
			names += name
		}
		return names
	}

	private fun namesOf(names: Set<String>): JsonArray {
		val array = JsonArray()
		for (name in names) array.add(name)
		return array
	}
}
