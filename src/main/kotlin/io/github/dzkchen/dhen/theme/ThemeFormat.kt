package io.github.dzkchen.dhen.theme

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenTheme
import io.github.dzkchen.dhen.util.Color
import org.slf4j.LoggerFactory

internal data class ThemeEntry(
	val id: String,
	val name: String,
	val version: String,
	val authors: List<String>,
	val theme: DhenTheme,
	val document: JsonObject?
)

internal object ThemeFormat {
	const val SCHEMA = 1
	const val MANIFEST = "theme.json"

	private const val SCHEMA_KEY = "schema"
	private const val ID_KEY = "id"
	private const val NAME_KEY = "name"
	private const val VERSION_KEY = "version"
	private const val AUTHORS_KEY = "authors"
	private const val COLORS_KEY = "colors"
	private const val MOTION_KEY = "motion"

	private const val HASH = '#'
	private const val RGB_LENGTH = 7
	private const val ARGB_LENGTH = 9
	private const val DECIMAL_SPAN = 10
	private const val NIBBLE = 4
	private const val MOTION_MAX = 10_000.0

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	private val COLORS: Map<String, DhenTheme.(Int) -> DhenTheme> = mapOf(
		"canvas" to { copy(canvas = it) },
		"surface" to { copy(surface = it) },
		"surfaceRaised" to { copy(surfaceRaised = it) },
		"surfaceInteractive" to { copy(surfaceInteractive = it) },
		"border" to { copy(border = it) },
		"textPrimary" to { copy(textPrimary = it) },
		"textSecondary" to { copy(textSecondary = it) },
		"textDisabled" to { copy(textDisabled = it) },
		"textOnAccent" to { copy(textOnAccent = it) },
		"splashCanvas" to { copy(splashCanvas = it) },
		"splashTrack" to { copy(splashTrack = it) },
		"splashInk" to { copy(splashInk = it) },
		"glassCanvas" to { copy(glassCanvas = it) },
		"glassSurface" to { copy(glassSurface = it) },
		"glassSurfaceRaised" to { copy(glassSurfaceRaised = it) },
		"glassSurfaceInteractive" to { copy(glassSurfaceInteractive = it) },
		"glassScrim" to { copy(glassScrim = it) },
		"glassShadow" to { copy(glassShadow = it) },
		"glassSheen" to { copy(glassSheen = it) },
		"glassVeil" to { copy(glassVeil = it) },
		"accent" to { copy(accent = it) }
	)

	private val MOTION: Map<String, DhenTheme.(Double) -> DhenTheme> = mapOf(
		"entryMillis" to { copy(entryMillis = it.toLong()) },
		"entryRise" to { copy(entryRise = it.toFloat()) },
		"tabMillis" to { copy(tabMillis = it.toLong()) },
		"tabSlide" to { copy(tabSlide = it.toFloat()) },
		"toggleMillis" to { copy(toggleMillis = it.toLong()) }
	)

	fun parse(id: String, document: JsonObject): ThemeEntry {
		val declared = number(document.get(SCHEMA_KEY))?.toInt() ?: SCHEMA
		if (declared > SCHEMA) log.info("Theme '{}' is written for schema {}; reading it as {}", id, declared, SCHEMA)
		val declaredId = text(document, ID_KEY)
		if (declaredId != null && !declaredId.equals(id, ignoreCase = true)) {
			log.warn("Theme '{}' calls itself '{}'; a theme is named by the folder it lives in", id, declaredId)
		}
		val colored = readColors(id, block(id, document, COLORS_KEY), DhenTheme.DEFAULT)
		val theme = readMotion(id, block(id, document, MOTION_KEY), colored)
		return ThemeEntry(id, text(document, NAME_KEY) ?: id, text(document, VERSION_KEY) ?: "", authors(document), theme, document)
	}

	private fun readColors(id: String, block: JsonObject?, base: DhenTheme): DhenTheme {
		var theme = base
		if (block == null) return theme
		for ((token, element) in block.entrySet()) {
			val slot = COLORS[token] ?: continue
			val color = argb(element)
			if (color == null) {
				log.warn("Theme '{}' has an unreadable color for '{}': {}", id, token, element)
				continue
			}
			theme = slot(theme, color)
		}
		return theme
	}

	private fun readMotion(id: String, block: JsonObject?, base: DhenTheme): DhenTheme {
		var theme = base
		if (block == null) return theme
		for ((token, element) in block.entrySet()) {
			val slot = MOTION[token] ?: continue
			val value = number(element)
			if (value == null || value < 0.0 || value > MOTION_MAX) {
				log.warn("Theme '{}' has an unreadable motion value for '{}': {}", id, token, element)
				continue
			}
			theme = slot(theme, value)
		}
		return theme
	}

	private fun block(id: String, document: JsonObject, key: String): JsonObject? {
		val element = document.get(key) ?: return null
		val block = element as? JsonObject
		if (block == null) log.warn("Theme '{}' has a '{}' entry that is not a block of tokens: {}", id, key, element)
		return block
	}

	private fun argb(element: JsonElement): Int? {
		val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: return null
		if (text.length != RGB_LENGTH && text.length != ARGB_LENGTH) return null
		if (text[0] != HASH) return null
		var value = 0
		for (i in 1 until text.length) {
			val digit = digit(text[i])
			if (digit < 0) return null
			value = (value shl NIBBLE) or digit
		}
		return if (text.length == RGB_LENGTH) Color(value).opaque().argb else value
	}

	private fun digit(char: Char): Int = when (char) {
		in '0'..'9' -> char - '0'
		in 'a'..'f' -> char - 'a' + DECIMAL_SPAN
		in 'A'..'F' -> char - 'A' + DECIMAL_SPAN
		else -> -1
	}

	private fun number(element: JsonElement?): Double? =
		(element as? JsonPrimitive)?.takeIf { it.isNumber }?.asDouble?.takeIf { it.isFinite() }

	private fun text(document: JsonObject, key: String): String? =
		(document.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString?.takeIf { it.isNotBlank() }

	private fun authors(document: JsonObject): List<String> {
		val array = document.get(AUTHORS_KEY) as? JsonArray ?: return emptyList()
		return array.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.asString }
	}
}
