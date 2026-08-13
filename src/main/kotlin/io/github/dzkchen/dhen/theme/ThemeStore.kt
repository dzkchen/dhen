package io.github.dzkchen.dhen.theme

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenTheme
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

internal object ThemeStore {
	const val DIRECTORY = "themes"
	const val DEFAULT_ID = "Default"
	const val LIGHT_ID = "Light"
	const val MAX_THEMES = 64
	const val MAX_NAME = 32

	private const val MAX_MANIFEST_BYTES = 64L * 1024L

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	val builtIn: List<ThemeEntry> = listOf(
		ThemeEntry(DEFAULT_ID, "Dhen", "", emptyList(), ThemeFormat.SCHEMA, DhenTheme.DEFAULT, null),
		ThemeEntry(LIGHT_ID, "Dhen Light", "", emptyList(), ThemeFormat.SCHEMA, DhenTheme.LIGHT, null)
	)

	@Volatile
	var themes: List<ThemeEntry> = builtIn
		private set

	@Volatile
	var ids: List<String> = builtIn.map { it.id }
		private set

	@Synchronized
	fun refresh(configRoot: Path): List<ThemeEntry> =
		(builtIn + discover(configRoot.resolve(DIRECTORY))).also {
			themes = it
			ids = it.map { entry -> entry.id }
		}

	fun find(id: String): ThemeEntry? = themes.firstOrNull { it.id.equals(id, ignoreCase = true) }

	fun reserved(id: String): Boolean = builtIn.any { it.id.equals(id, ignoreCase = true) }

	fun legal(id: String): Boolean =
		id.length in 1..MAX_NAME && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

	private fun discover(root: Path): List<ThemeEntry> {
		if (!Files.isDirectory(root)) return emptyList()
		val found = ArrayList<ThemeEntry>()
		for (folder in folders(root)) {
			if (found.size >= MAX_THEMES) {
				log.warn("Ignoring the theme folders past the first {} in {}", MAX_THEMES, root)
				break
			}
			val id = folder.fileName.toString()
			if (!legal(id)) {
				log.warn("Ignoring the theme folder '{}': a theme is named in up to {} letters, digits, '-' or '_'", id, MAX_NAME)
				continue
			}
			if (claimed(found, id)) {
				log.warn("Ignoring the theme folder '{}': another theme already answers to that name", id)
				continue
			}
			read(folder, id)?.let { found += it }
		}
		return found
	}

	private fun folders(root: Path): List<Path> {
		val paths = ArrayList<Path>()
		try {
			Files.newDirectoryStream(root).use { stream ->
				for (entry in stream) {
					if (Files.isDirectory(entry)) paths.add(entry)
				}
			}
		} catch (e: Exception) {
			log.warn("Could not read the theme directory {}", root, e)
		}
		paths.sortBy { it.fileName.toString().lowercase() }
		return paths
	}

	private fun claimed(found: List<ThemeEntry>, id: String): Boolean =
		reserved(id) || found.any { it.id.equals(id, ignoreCase = true) }

	private fun read(folder: Path, id: String): ThemeEntry? {
		val manifest = folder.resolve(ThemeFormat.MANIFEST)
		if (!Files.isRegularFile(manifest)) {
			log.warn("Ignoring the theme folder '{}': it holds no {}", id, ThemeFormat.MANIFEST)
			return null
		}
		val document = document(manifest, id) ?: return null
		return ThemeFormat.parse(id, document)
	}

	private fun document(manifest: Path, id: String): JsonObject? {
		try {
			if (Files.size(manifest) > MAX_MANIFEST_BYTES) {
				log.warn("Ignoring the theme '{}': its {} is too large to be a manifest", id, ThemeFormat.MANIFEST)
				return null
			}
			val document = JsonParser.parseString(Files.readString(manifest)) as? JsonObject
			if (document == null) log.warn("Ignoring the theme '{}': its {} is not a JSON object", id, ThemeFormat.MANIFEST)
			return document
		} catch (e: Exception) {
			log.warn("Ignoring the theme '{}': its {} could not be read", id, ThemeFormat.MANIFEST, e)
			return null
		}
	}
}
