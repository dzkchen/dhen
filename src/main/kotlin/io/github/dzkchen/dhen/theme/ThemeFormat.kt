package io.github.dzkchen.dhen.theme

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenTheme
import io.github.dzkchen.dhen.util.Color
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numberOrNull
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.textOrNull
import org.slf4j.LoggerFactory
import java.util.Locale

internal data class ThemeEntry(
	val id: String,
	val name: String,
	val version: String,
	val authors: List<String>,
	val schema: Int,
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
	private const val FONT_KEY = "font"
	private const val COLORS_KEY = "colors"
	private const val MOTION_KEY = "motion"

	private const val HASH = '#'
	private const val RGB_LENGTH = 7
	private const val ARGB_LENGTH = 9
	private const val DECIMAL_SPAN = 10
	private const val NIBBLE = 4
	private const val MOTION_MAX = 10_000.0

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	private class ColorToken(val read: (DhenTheme) -> Int, val write: DhenTheme.(Int) -> DhenTheme)

	private class MotionToken(val read: (DhenTheme) -> Number, val write: DhenTheme.(Double) -> DhenTheme)

	private val COLORS: Map<String, ColorToken> = mapOf(
		"canvas" to ColorToken(DhenTheme::canvas) { copy(canvas = it) },
		"surface" to ColorToken(DhenTheme::surface) { copy(surface = it) },
		"surfaceRaised" to ColorToken(DhenTheme::surfaceRaised) { copy(surfaceRaised = it) },
		"surfaceInteractive" to ColorToken(DhenTheme::surfaceInteractive) { copy(surfaceInteractive = it) },
		"border" to ColorToken(DhenTheme::border) { copy(border = it) },
		"textPrimary" to ColorToken(DhenTheme::textPrimary) { copy(textPrimary = it) },
		"textSecondary" to ColorToken(DhenTheme::textSecondary) { copy(textSecondary = it) },
		"textDisabled" to ColorToken(DhenTheme::textDisabled) { copy(textDisabled = it) },
		"textOnAccent" to ColorToken(DhenTheme::textOnAccent) { copy(textOnAccent = it) },
		"textOnWorld" to ColorToken(DhenTheme::textOnWorld) { copy(textOnWorld = it) },
		"splashCanvas" to ColorToken(DhenTheme::splashCanvas) { copy(splashCanvas = it) },
		"splashTrack" to ColorToken(DhenTheme::splashTrack) { copy(splashTrack = it) },
		"splashInk" to ColorToken(DhenTheme::splashInk) { copy(splashInk = it) },
		"glassCanvas" to ColorToken(DhenTheme::glassCanvas) { copy(glassCanvas = it) },
		"glassSurface" to ColorToken(DhenTheme::glassSurface) { copy(glassSurface = it) },
		"glassSurfaceRaised" to ColorToken(DhenTheme::glassSurfaceRaised) { copy(glassSurfaceRaised = it) },
		"glassSurfaceInteractive" to ColorToken(DhenTheme::glassSurfaceInteractive) { copy(glassSurfaceInteractive = it) },
		"glassScrim" to ColorToken(DhenTheme::glassScrim) { copy(glassScrim = it) },
		"glassShadow" to ColorToken(DhenTheme::glassShadow) { copy(glassShadow = it) },
		"glassSheen" to ColorToken(DhenTheme::glassSheen) { copy(glassSheen = it) },
		"glassVeil" to ColorToken(DhenTheme::glassVeil) { copy(glassVeil = it) },
		"accent" to ColorToken(DhenTheme::accent) { copy(accent = it) }
	)

	private val MOTION: Map<String, MotionToken> = mapOf(
		"entryMillis" to MotionToken(DhenTheme::entryMillis) { copy(entryMillis = it.toLong()) },
		"entryRise" to MotionToken(DhenTheme::entryRise) { copy(entryRise = it.toFloat()) },
		"tabMillis" to MotionToken(DhenTheme::tabMillis) { copy(tabMillis = it.toLong()) },
		"tabSlide" to MotionToken(DhenTheme::tabSlide) { copy(tabSlide = it.toFloat()) },
		"toggleMillis" to MotionToken(DhenTheme::toggleMillis) { copy(toggleMillis = it.toLong()) }
	)

	fun parse(id: String, document: JsonObject): ThemeEntry {
		val authorsSchema = document.get(SCHEMA_KEY)
		val declared = authorsSchema.numberOrNull()?.toInt() ?: SCHEMA
		if (declared > SCHEMA) log.info("Theme '{}' is written for schema {}; reading it as {}", id, authorsSchema, SCHEMA)
		val declaredId = document.text(ID_KEY)
		if (declaredId != null && !declaredId.equals(id, ignoreCase = true)) {
			log.warn("Theme '{}' calls itself '{}'; a theme is named by the folder it lives in", id, declaredId)
		}
		val colored = readColors(id, block(id, document, COLORS_KEY), DhenTheme.DEFAULT)
		val moved = readMotion(id, block(id, document, MOTION_KEY), colored)
		val theme = readFont(id, document, moved)
		return ThemeEntry(id, document.text(NAME_KEY) ?: id, document.text(VERSION_KEY) ?: "", authors(document), declared, theme, document)
	}

	fun document(id: String, metadata: ThemeEntry?, resolved: DhenTheme): JsonObject {
		val document = metadata?.document?.deepCopy() ?: JsonObject()
		stampSchema(document, metadata?.schema ?: SCHEMA)
		document.addProperty(ID_KEY, id)
		document.addProperty(NAME_KEY, id)
		document.addProperty(VERSION_KEY, metadata?.version ?: "")
		document.add(AUTHORS_KEY, JsonArray().apply { metadata?.authors?.forEach { add(it) } })
		document.addProperty(FONT_KEY, resolved.font)
		val colors = section(document, COLORS_KEY)
		for ((token, slot) in COLORS) colors.addProperty(token, hex(slot.read(resolved)))
		val motion = section(document, MOTION_KEY)
		for ((token, slot) in MOTION) motion.addProperty(token, slot.read(resolved))
		return document
	}

	private fun stampSchema(document: JsonObject, declared: Int) {
		val keepAuthorsSchema = declared > SCHEMA && document.number(SCHEMA_KEY)?.toInt() == declared
		if (!keepAuthorsSchema) document.addProperty(SCHEMA_KEY, maxOf(SCHEMA, declared))
	}

	private fun section(document: JsonObject, key: String): JsonObject =
		document.obj(key) ?: JsonObject().also { document.add(key, it) }

	private fun hex(argb: Int): String = String.format(Locale.ROOT, "#%08X", argb)

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
			theme = slot.write(theme, color)
		}
		return theme
	}

	private fun readFont(id: String, document: JsonObject, base: DhenTheme): DhenTheme {
		val element = document.get(FONT_KEY) ?: return base
		val name = element.textOrNull()?.takeIf(ThemeStore::legal)
		if (name == null) {
			log.warn("Theme '{}' names a face it cannot be drawn in: {}", id, element)
			return base
		}
		return base.copy(font = name)
	}

	private fun readMotion(id: String, block: JsonObject?, base: DhenTheme): DhenTheme {
		var theme = base
		if (block == null) return theme
		for ((token, element) in block.entrySet()) {
			val slot = MOTION[token] ?: continue
			val value = element.numberOrNull()
			if (value == null || value < 0.0 || value > MOTION_MAX) {
				log.warn("Theme '{}' has an unreadable motion value for '{}': {}", id, token, element)
				continue
			}
			theme = slot.write(theme, value)
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
		val text = element.textOrNull() ?: return null
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

	private fun authors(document: JsonObject): List<String> =
		document.array(AUTHORS_KEY)?.mapNotNull(JsonElement::textOrNull).orEmpty()
}
