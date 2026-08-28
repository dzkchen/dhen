package io.github.dzkchen.dhen.config

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.gui.ClickGuiState
import io.github.dzkchen.dhen.gui.ClickGuiView
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.Effects
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.HudPersistence
import io.github.dzkchen.dhen.util.flagOrNull
import io.github.dzkchen.dhen.util.obj

internal object CorePersistence {
	private const val WELCOME_SHOWN = "welcomeShown"
	private const val DRAGGABLE_PANELS = "panels"
	private const val EFFECTS_BLOCK = "effects"
	private const val REDUCED_KEY = "reduced"
	private const val ACCORDION_OPENED = "opened"
	private const val HUD = "hud"

	private val RETIRED_CLIENT_KEYS = arrayOf("Layout", "Arrow keys")

	val migrations: List<(JsonObject) -> Unit> = listOf(
		{ doc: JsonObject -> doc.remove(DRAGGABLE_PANELS) },
		{ doc: JsonObject -> moveEffectsFlagIntoClientBlock(doc) },
		{ doc: JsonObject -> dropRetiredLayoutKeys(doc) }
	)

	fun apply(doc: JsonObject, hudElements: List<HudElement> = emptyList()): CoreState {
		ClientPrefs.read(doc)
		doc.obj(HUD)?.let { HudPersistence.apply(hudElements, it) }
		return CoreState(
			clickGui = ClickGuiView.read(doc),
			welcomeShown = doc.get(WELCOME_SHOWN).flagOrNull() ?: false
		)
	}

	fun snapshot(view: ClickGuiState, welcomeShown: Boolean, hudElements: List<HudElement> = emptyList()): JsonObject =
		ClientPrefs.writeInto(ClickGuiView.writeInto(JsonObject(), view)).apply {
			addProperty(WELCOME_SHOWN, welcomeShown)
			if (hudElements.isNotEmpty()) add(HUD, HudPersistence.snapshot(hudElements))
		}

	private fun dropRetiredLayoutKeys(doc: JsonObject) {
		doc.obj(ClickGuiView.CLICK_GUI)?.remove(ACCORDION_OPENED)
		val client = doc.obj(ClientPrefs.CLIENT) ?: return
		for (key in RETIRED_CLIENT_KEYS) client.remove(key)
	}

	private fun moveEffectsFlagIntoClientBlock(doc: JsonObject) {
		val effects = doc.remove(EFFECTS_BLOCK) as? JsonObject ?: return
		val stored = effects.get(REDUCED_KEY).flagOrNull() ?: return
		val client = doc.obj(ClientPrefs.CLIENT) ?: JsonObject().also { doc.add(ClientPrefs.CLIENT, it) }
		if (!client.has(Effects.REDUCED)) client.addProperty(Effects.REDUCED, stored)
	}
}

internal class CoreState(
	val clickGui: ClickGuiState,
	val welcomeShown: Boolean
)
