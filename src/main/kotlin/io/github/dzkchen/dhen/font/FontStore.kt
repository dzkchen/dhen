package io.github.dzkchen.dhen.font

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.theme.ThemeStore
import io.github.dzkchen.dhen.util.numberOrNull
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

internal class FontFace(
	val name: String,
	val id: String,
	val file: Path,
	val size: Double,
	val oversample: Double,
	val shiftX: Double,
	val shiftY: Double
)

internal object FontStore {
	const val DIRECTORY = "fonts"
	const val INTER = "Inter"
	const val VANILLA = "Vanilla"
	const val MAX_FONTS = 32
	const val EXTENSION = ".ttf"
	const val SIDECAR = ".json"

	const val SIZE_KEY = "size"
	const val OVERSAMPLE_KEY = "oversample"
	const val SHIFT_KEY = "shift"

	const val DEFAULT_SIZE = 9.5
	const val DEFAULT_OVERSAMPLE = 4.0

	private const val MAX_FACE_BYTES = 16L * 1024L * 1024L
	private const val MAX_SIDECAR_BYTES = 8L * 1024L
	private const val MIN_SIZE = 0.5
	private const val MAX_SIZE = 128.0
	private const val MIN_OVERSAMPLE = 1.0
	private const val MAX_OVERSAMPLE = 16.0
	private const val MAX_SHIFT = 64.0
	private const val HORIZONTAL = 0
	private const val VERTICAL = 1
	private const val SHIFT_PAIR = 2

	private val truetypeTag = byteArrayOf(0, 1, 0, 0)

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	@Volatile
	private var root: Path? = null

	@Volatile
	private var discovered: List<FontFace>? = null

	@Volatile
	var names: List<String> = listOf(INTER)
		private set

	fun install(configRoot: Path) {
		root = configRoot
	}

	fun faces(): List<FontFace> = discovered ?: scanned()

	@Synchronized
	fun refresh(): List<FontFace> = scan()

	@Synchronized
	private fun scanned(): List<FontFace> = if (root == null) emptyList() else discovered ?: scan()

	fun find(name: String): FontFace? = discovered?.firstOrNull { it.name.equals(name, ignoreCase = true) }

	fun reserved(name: String): Boolean = name.equals(INTER, ignoreCase = true) || name.equals(VANILLA, ignoreCase = true)

	internal fun resetForTest() {
		root = null
		discovered = null
		names = listOf(INTER)
	}

	private fun scan(): List<FontFace> = discover().also {
		discovered = it
		names = listOf(INTER) + it.map(FontFace::name)
	}

	private fun discover(): List<FontFace> {
		val folder = root?.resolve(DIRECTORY) ?: return emptyList()
		val found = ArrayList<FontFace>()
		for (file in candidates(folder)) {
			if (found.size >= MAX_FONTS) {
				log.warn("Ignoring the font files past the first {} in {}", MAX_FONTS, folder)
				break
			}
			read(file, found)?.let { found += it }
		}
		return found
	}

	private fun candidates(folder: Path): List<Path> {
		val paths = ArrayList<Path>()
		try {
			Files.createDirectories(folder)
			Files.newDirectoryStream(folder).use { stream ->
				for (entry in stream) {
					if (Files.isRegularFile(entry) && entry.fileName.toString().endsWith(EXTENSION, ignoreCase = true)) {
						paths.add(entry)
					}
				}
			}
		} catch (e: Exception) {
			log.warn("Could not read the fonts directory {}", folder, e)
		}
		paths.sortBy { it.fileName.toString().lowercase() }
		return paths
	}

	private fun read(file: Path, found: List<FontFace>): FontFace? {
		val name = file.fileName.toString().dropLast(EXTENSION.length)
		if (!ThemeStore.legal(name)) {
			log.warn("Ignoring the font '{}': a font is named in up to {} letters, digits, '-' or '_'", name, ThemeStore.MAX_NAME)
			return null
		}
		if (reserved(name) || found.any { it.name.equals(name, ignoreCase = true) }) {
			log.warn("Ignoring the font '{}': another font already answers to that name", name)
			return null
		}
		if (!truetype(file, name)) return null
		return tuned(file, name)
	}

	private fun truetype(file: Path, name: String): Boolean {
		try {
			val bytes = Files.size(file)
			if (bytes > MAX_FACE_BYTES) {
				log.warn("Ignoring the font '{}': {} bytes is too large to be a face", name, bytes)
				return false
			}
			val tag = Files.newInputStream(file).use { it.readNBytes(truetypeTag.size) }
			if (tag.contentEquals(truetypeTag)) return true
			log.warn("Ignoring the font '{}': the vanilla loader reads TrueType outlines only, not OpenType or collections", name)
		} catch (e: Exception) {
			log.warn("Ignoring the font '{}': it could not be read", name, e)
		}
		return false
	}

	private fun tuned(file: Path, name: String): FontFace {
		val sidecar = document(file.resolveSibling(name + SIDECAR), name)
		val shift = shift(sidecar, name)
		return FontFace(
			name,
			name.lowercase(),
			file,
			scalar(sidecar?.get(SIZE_KEY), name, SIZE_KEY, DEFAULT_SIZE, MIN_SIZE, MAX_SIZE),
			scalar(sidecar?.get(OVERSAMPLE_KEY), name, OVERSAMPLE_KEY, DEFAULT_OVERSAMPLE, MIN_OVERSAMPLE, MAX_OVERSAMPLE),
			scalar(shift?.get(HORIZONTAL), name, SHIFT_KEY, 0.0, -MAX_SHIFT, MAX_SHIFT),
			scalar(shift?.get(VERTICAL), name, SHIFT_KEY, 0.0, -MAX_SHIFT, MAX_SHIFT)
		)
	}

	private fun shift(sidecar: JsonObject?, name: String): JsonArray? {
		val element = sidecar?.get(SHIFT_KEY) ?: return null
		val pair = element as? JsonArray
		if (pair == null || pair.size() < SHIFT_PAIR) {
			log.warn("Font '{}' has an unreadable '{}': {}", name, SHIFT_KEY, element)
			return null
		}
		return pair
	}

	private fun scalar(element: JsonElement?, name: String, key: String, fallback: Double, low: Double, high: Double): Double {
		if (element == null) return fallback
		val value = element.numberOrNull()
		if (value == null || value < low || value > high) {
			log.warn("Font '{}' has an unreadable '{}': {}", name, key, element)
			return fallback
		}
		return value
	}

	private fun document(sidecar: Path, name: String): JsonObject? {
		if (!Files.isRegularFile(sidecar)) return null
		try {
			if (Files.size(sidecar) > MAX_SIDECAR_BYTES) {
				log.warn("Font '{}' has a {} too large to be settings; using the bundled face's values", name, SIDECAR)
				return null
			}
			val document = JsonParser.parseString(Files.readString(sidecar)) as? JsonObject
			if (document == null) log.warn("Font '{}' has a {} that is not a JSON object", name, SIDECAR)
			return document
		} catch (e: Exception) {
			log.warn("Font '{}' has a {} that could not be read", name, SIDECAR, e)
			return null
		}
	}
}
