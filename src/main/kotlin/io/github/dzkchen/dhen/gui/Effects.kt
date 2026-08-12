package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal object Effects {
	private const val EFFECTS = "effects"
	private const val REDUCED = "reduced"

	var reduced = false

	fun read(doc: JsonObject) {
		val effects = doc.get(EFFECTS) as? JsonObject
		val stored = effects?.get(REDUCED) as? JsonPrimitive
		reduced = stored?.takeIf { it.isBoolean }?.asBoolean ?: false
	}

	fun writeInto(doc: JsonObject): JsonObject {
		doc.add(EFFECTS, JsonObject().apply { addProperty(REDUCED, reduced) })
		return doc
	}
}
