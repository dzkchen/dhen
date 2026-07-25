package io.github.dzkchen.dhen.gui

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal data class PanelState(var x: Int, var y: Int, var collapsed: Boolean)

internal object ClickGuiLayout {
	private const val PANELS = "panels"
	private const val X = "x"
	private const val Y = "y"
	private const val COLLAPSED = "collapsed"

	fun read(doc: JsonObject): MutableMap<String, PanelState> {
		val panels = doc.get(PANELS) as? JsonObject ?: return linkedMapOf()
		val states = linkedMapOf<String, PanelState>()
		for ((key, element) in panels.entrySet()) {
			val entry = element as? JsonObject ?: continue
			val x = entry.get(X).asIntOrNull() ?: continue
			val y = entry.get(Y).asIntOrNull() ?: continue
			states[key] = PanelState(x, y, entry.get(COLLAPSED).asBooleanOrNull() ?: false)
		}
		return states
	}

	fun write(states: Map<String, PanelState>): JsonObject {
		val panels = JsonObject()
		for ((key, state) in states) {
			panels.add(key, JsonObject().apply {
				addProperty(X, state.x)
				addProperty(Y, state.y)
				addProperty(COLLAPSED, state.collapsed)
			})
		}
		return JsonObject().apply { add(PANELS, panels) }
	}

	private fun JsonElement?.asIntOrNull(): Int? =
		(this as? JsonPrimitive)?.takeIf { it.isNumber }?.asInt

	private fun JsonElement?.asBooleanOrNull(): Boolean? =
		(this as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean
}
