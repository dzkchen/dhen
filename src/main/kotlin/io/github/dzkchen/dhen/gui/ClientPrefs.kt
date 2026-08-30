package io.github.dzkchen.dhen.gui

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.config.Setting
import io.github.dzkchen.dhen.config.Setting.Companion.hide
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.config.StringSetting
import io.github.dzkchen.dhen.font.FontStore
import io.github.dzkchen.dhen.theme.ThemeStore
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.obj
import org.slf4j.LoggerFactory

internal class PrefSection(val title: String, val settings: List<Setting<*>>)

internal object ClientPrefs {
	const val CLIENT = "client"

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	val theme = SelectorSetting(
		"Theme",
		ThemeStore.DEFAULT_ID,
		ThemeStore.ids,
		listed = true,
		description = "Palette every Dhen surface draws from."
	)

	val accent = ColorSetting(
		"Accent color",
		Color(DhenPalette.DEFAULT_ACCENT),
		description = "Highlight color for every Dhen surface."
	)

	val dhenFont = BooleanSetting(
		"Dhen font",
		true,
		description = "Draw Dhen text and Minecraft's default font in the selected face."
	)

	val font = SelectorSetting(
		"Font",
		FontStore.INTER,
		FontStore.names,
		listed = true,
		description = "Face Dhen and Minecraft's default text draw in right now."
	)

	val addFont = ActionSetting(
		"Add font...",
		description = "Choose a TrueType file; Dhen copies it in and starts using it."
	)

	val browseFonts = ActionSetting(
		"Open fonts folder",
		description = "Show the folder fonts are dropped into in your file browser."
	)

	val reloadFonts = ActionSetting(
		"Reload fonts",
		description = "Re-read files you changed by hand in the fonts folder."
	)

	val reload = ActionSetting(
		"Reload themes",
		description = "Read the themes folder again without restarting."
	)

	val browse = ActionSetting(
		"Open themes folder",
		description = "Show the folder themes are dropped into in your file browser."
	)

	val openSoundManager = ActionSetting(
		"Open Sound Manager",
		description = "Browse every game sound and adjust its volume."
	)

	val splash = BooleanSetting(
		"Splash screen",
		true,
		description = "Show Dhen's loading screen while the game starts and reloads resources."
	)

	val chatAlerts = BooleanSetting(
		"Chat alerts",
		true,
		description = "Write a line in chat when a privacy feature blocks or spots something."
	)

	val toastPopups = BooleanSetting(
		"Toast popups",
		true,
		description = "Raise a card in the bottom-right corner when a privacy feature blocks or spots something."
	)

	val logEvents = BooleanSetting(
		"Log events",
		true,
		description = "Record every privacy detection in the game log."
	)

	val blockLocalUrls = BooleanSetting(
		"Block Local URLs",
		true,
		description = "Refuse a server's download when its address points back at this machine or your home network."
	)

	val keyResolutionSpoofing = BooleanSetting(
		"Key Resolution Spoofing",
		true,
		description = "Hide mod translation values that a server asks this client to resolve."
	)

	val debugAlerts = BooleanSetting(
		"Debug alerts",
		false,
		description = "Add the packet and key behind each detection to its chat line."
	)

	val alertHintShown = BooleanSetting("Alert Hint Shown").hide()

	val profileProxy = StringSetting(
		"Profile proxy",
		"",
		200,
		"Base address of the profile service Dhen asks for player data."
	).hide()

	val sections: List<PrefSection> = listOf(
		PrefSection("Effects", listOf(Effects.reducedSetting)),
		PrefSection("Appearance", listOf(theme, accent, dhenFont, font, addFont, browseFonts, reloadFonts, reload, browse)),
		PrefSection("Sounds", listOf(openSoundManager)),
		PrefSection(
			"Privacy",
			listOf(blockLocalUrls, keyResolutionSpoofing, chatAlerts, toastPopups, logEvents, debugAlerts, alertHintShown)
		),
		PrefSection("Client", listOf(splash, profileProxy))
	)

	private val stored: List<Setting<*>> = sections.flatMap { it.settings }

	private var inheritedFrom = theme.preferred

	fun sync(): Boolean {
		if (theme.preferred != inheritedFrom) inherit()
		applySelection(selected())
		return DhenFont.synchronize(dhenFont.on, font.value)
	}

	private fun inherit() {
		val chosen = ThemeStore.find(theme.preferred)?.theme ?: return
		inheritedFrom = theme.preferred
		accent.value = Color(chosen.accent)
		dhenFont.on = !chosen.font.equals(FontStore.VANILLA, ignoreCase = true)
		if (!dhenFont.on) return
		font.value = chosen.font
		reportMissingFace()
	}

	private fun reportMissingFace() {
		if (font.options.none { it.equals(font.preferred, ignoreCase = true) }) {
			log.warn("Font '{}' is not in the fonts folder; drawing '{}' until it is back", font.preferred, font.value)
		}
	}

	fun adopt(): Boolean {
		theme.options = ThemeStore.ids
		if (ThemeStore.find(theme.preferred) == null) {
			log.warn("Theme '{}' is not in the themes folder; drawing '{}' until it is back", theme.preferred, theme.value)
		}
		font.options = FontStore.names
		reportMissingFace()
		return sync()
	}

	fun read(doc: JsonObject) {
		val client = doc.obj(CLIENT)
		if (client?.has(dhenFont.name) != true) dhenFont.reset()
		SettingCodec.readInto(client, stored, CLIENT)
		inheritedFrom = theme.preferred
		DhenFont.synchronize(dhenFont.on, font.value)
		applySelection(selected())
	}

	private fun selected(): DhenTheme = ThemeStore.find(theme.value)?.theme ?: DhenTheme.DEFAULT

	private fun face(): String = if (dhenFont.on) font.value else FontStore.VANILLA

	private fun applySelection(selected: DhenTheme) {
		DhenTheme.activate(selected.resolved(accent.value.argb, face()))
	}

	fun writeInto(doc: JsonObject): JsonObject {
		val block = doc.obj(CLIENT) ?: JsonObject().also { doc.add(CLIENT, it) }
		SettingCodec.writeInto(block, stored)
		return doc
	}
}
