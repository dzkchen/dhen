package io.github.dzkchen.dhen.gui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal class ClickGuiState(
	val collapsed: MutableSet<String> = linkedSetOf(),
	val opened: MutableSet<String> = linkedSetOf()
) {
	fun toggle(category: String, accordion: Boolean) {
		val names = if (accordion) opened else collapsed
		if (!names.remove(category)) names.add(category)
	}

	fun isBodyHidden(category: String, accordion: Boolean): Boolean =
		if (accordion) category !in opened else category in collapsed
}

internal object ClickGuiView {
	private const val CLICK_GUI = "clickgui"
	private const val COLLAPSED = "collapsed"
	private const val OPENED = "opened"

	fun read(doc: JsonObject): ClickGuiState {
		val block = doc.get(CLICK_GUI) as? JsonObject ?: return ClickGuiState()
		return ClickGuiState(namesIn(block, COLLAPSED), namesIn(block, OPENED))
	}

	fun writeInto(doc: JsonObject, state: ClickGuiState): JsonObject {
		val block = doc.get(CLICK_GUI) as? JsonObject ?: JsonObject().also { doc.add(CLICK_GUI, it) }
		block.add(COLLAPSED, namesOf(state.collapsed))
		block.add(OPENED, namesOf(state.opened))
		return doc
	}

	private fun namesIn(block: JsonObject, key: String): MutableSet<String> {
		val names = linkedSetOf<String>()
		val stored = block.get(key) as? JsonArray ?: return names
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
