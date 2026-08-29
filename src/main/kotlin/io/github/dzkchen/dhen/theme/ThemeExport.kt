package io.github.dzkchen.dhen.theme

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenTheme
import io.github.dzkchen.dhen.util.JsonFile
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

internal object ThemeExport {
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	fun write(root: Path, name: String, metadata: ThemeEntry?, resolved: DhenTheme): Path {
		val themes = root.resolve(ThemeStore.DIRECTORY)
		Files.createDirectories(themes)
		val folder = claim(themes, name) ?: throw IOException("$name and its copies are taken")
		try {
			val document = ThemeFormat.document(folder.fileName.toString(), metadata, resolved)
			JsonFile.writeAtomic(folder.resolve(ThemeFormat.MANIFEST), JsonFile.pretty.toJson(document))
		} catch (e: Exception) {
			discard(folder)
			throw e
		}
		return folder
	}

	private fun discard(folder: Path) {
		try {
			Files.newDirectoryStream(folder).use { stream -> stream.forEach { Files.deleteIfExists(it) } }
			Files.deleteIfExists(folder)
		} catch (e: Exception) {
			log.warn("Left an empty theme folder behind at {}", folder, e)
		}
	}

	private fun claim(themes: Path, name: String): Path? {
		var folder: Path? = null
		ThemeStore.claim(name) { id -> !ThemeStore.reserved(id) && created(themes.resolve(id))?.also { folder = it } != null }
		return folder
	}

	private fun created(folder: Path): Path? = try {
		Files.createDirectory(folder)
	} catch (_: FileAlreadyExistsException) {
		null
	}
}
