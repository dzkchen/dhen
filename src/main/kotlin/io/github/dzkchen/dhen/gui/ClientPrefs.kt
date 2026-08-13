package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.theme.ThemeStore
import io.github.dzkchen.dhen.util.Color
import org.slf4j.LoggerFactory

internal class PrefSection(val title: String, val settings: List<Setting<*>>)

internal object ClientPrefs {
	const val CLIENT = "client"

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	val theme = SelectorSetting(
		"Theme",
		ThemeStore.DEFAULT_ID,
		ThemeStore.ids,
		description = "Palette every Dhen surface draws from."
	)

	val accent = ColorSetting(
		"Accent color",
		Color(DhenPalette.DEFAULT_ACCENT),
		description = "Highlight color for every Dhen surface."
	)

	val reload = ActionSetting(
		"Reload themes",
		description = "Read the themes folder again without restarting."
	)

	val browse = ActionSetting(
		"Open themes folder",
		description = "Show the folder themes are dropped into in your file browser."
	)

	val splash = BooleanSetting(
		"Splash screen",
		true,
		description = "Show Dhen's loading screen while the game starts and reloads resources."
	)

	val sections: List<PrefSection> = listOf(
		PrefSection("Effects", listOf(Effects.reducedSetting)),
		PrefSection("Appearance", listOf(theme, accent, reload, browse)),
		PrefSection("Client", listOf(splash))
	)

	private val stored: List<Setting<*>> = sections.flatMap { it.settings }

	private var accentSource = theme.preferred

	fun sync() {
		val selected = selected()
		if (theme.preferred != accentSource) accent.value = Color(selected.accent)
		applySelection(selected)
	}

	fun adopt() {
		theme.options = ThemeStore.ids
		if (ThemeStore.find(theme.preferred) == null) {
			log.warn("Theme '{}' is not in the themes folder; drawing '{}' until it is back", theme.preferred, theme.value)
		}
		sync()
	}

	fun read(doc: JsonObject) {
		SettingCodec.readInto(doc.get(CLIENT) as? JsonObject, stored, CLIENT)
		applySelection(selected())
	}

	private fun selected(): DhenTheme = ThemeStore.find(theme.value)?.theme ?: DhenTheme.DEFAULT

	private fun applySelection(selected: DhenTheme) {
		accentSource = theme.preferred
		DhenTheme.activate(selected.withAccent(accent.value.argb))
	}

	fun writeInto(doc: JsonObject): JsonObject {
		val block = doc.get(CLIENT) as? JsonObject ?: JsonObject().also { doc.add(CLIENT, it) }
		SettingCodec.writeInto(block, stored)
		return doc
	}
}
