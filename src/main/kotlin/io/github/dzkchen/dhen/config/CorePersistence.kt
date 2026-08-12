package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.dzkchen.dhen.gui.ClickGuiState
import io.github.dzkchen.dhen.gui.ClickGuiView
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.Effects

internal object CorePersistence {
	private const val DRAGGABLE_PANELS = "panels"
	private const val EFFECTS_BLOCK = "effects"
	private const val REDUCED_KEY = "reduced"

	val migrations: List<(JsonObject) -> Unit> = listOf(
		{ doc: JsonObject -> doc.remove(DRAGGABLE_PANELS) },
		{ doc: JsonObject -> moveEffectsFlagIntoClientBlock(doc) }
	)

	fun apply(doc: JsonObject): ClickGuiState {
		ClientPrefs.read(doc)
		return ClickGuiView.read(doc)
	}

	fun snapshot(view: ClickGuiState): JsonObject =
		ClientPrefs.writeInto(ClickGuiView.writeInto(JsonObject(), view))

	private fun moveEffectsFlagIntoClientBlock(doc: JsonObject) {
		val effects = doc.remove(EFFECTS_BLOCK) as? JsonObject ?: return
		val stored = (effects.get(REDUCED_KEY) as? JsonPrimitive)?.takeIf { it.isBoolean } ?: return
		val client = doc.get(ClientPrefs.CLIENT) as? JsonObject ?: JsonObject().also { doc.add(ClientPrefs.CLIENT, it) }
		if (!client.has(Effects.REDUCED)) client.add(Effects.REDUCED, stored)
	}
}
