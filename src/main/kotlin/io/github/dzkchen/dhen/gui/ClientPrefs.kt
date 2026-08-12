package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.util.Color

internal class PrefSection(val title: String, val settings: List<Setting<*>>)

internal object ClientPrefs {
	const val CLIENT = "client"

	val accent = ColorSetting(
		"Accent color",
		Color(DhenPalette.DEFAULT_ACCENT),
		description = "Highlight color for every Dhen surface."
	)

	val splash = BooleanSetting(
		"Splash screen",
		true,
		description = "Show Dhen's loading screen while the game starts and reloads resources."
	)

	val sections: List<PrefSection> = listOf(
		PrefSection("Effects", listOf(Effects.reducedSetting)),
		PrefSection("Appearance", listOf(accent)),
		PrefSection("Client", listOf(splash))
	)

	private val stored: List<Setting<*>> = sections.flatMap { it.settings }

	fun sync() {
		DhenPalette.accent = accent.value.argb
	}

	fun read(doc: JsonObject) {
		SettingCodec.readInto(doc.get(CLIENT) as? JsonObject, stored, CLIENT)
		sync()
	}

	fun writeInto(doc: JsonObject): JsonObject {
		val block = doc.get(CLIENT) as? JsonObject ?: JsonObject().also { doc.add(CLIENT, it) }
		SettingCodec.writeInto(block, stored)
		return doc
	}
}
