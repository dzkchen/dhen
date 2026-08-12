package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.gui.ClickGuiView
import io.github.dzkchen.dhen.gui.Effects

internal object CorePersistence {
	private const val DRAGGABLE_PANELS = "panels"

	val migrations: List<(JsonObject) -> Unit> = listOf({ doc: JsonObject -> doc.remove(DRAGGABLE_PANELS) })

	fun apply(doc: JsonObject): MutableSet<String> {
		Effects.read(doc)
		return ClickGuiView.read(doc)
	}

	fun snapshot(collapsedCategories: Set<String>): JsonObject =
		Effects.writeInto(ClickGuiView.writeInto(JsonObject(), collapsedCategories))
}
