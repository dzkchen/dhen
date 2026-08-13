package io.github.dzkchen.dhen.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object JsonFile {
	private const val TEMP = ".tmp"

	val pretty: Gson = GsonBuilder().setPrettyPrinting().create()

	fun writeAtomic(path: Path, text: String) {
		path.parent?.let { Files.createDirectories(it) }
		val temp = path.resolveSibling(path.fileName.toString() + TEMP)
		Files.writeString(temp, text)
		try {
			Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
		} catch (_: AtomicMoveNotSupportedException) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
		}
	}
}
