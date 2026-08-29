package io.github.dzkchen.dhen.font

import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Path

internal object FontChooser {
	private const val TITLE = "Choose a font for Dhen"
	private const val DESCRIPTION = "TrueType font (*${FontStore.EXTENSION})"
	private const val PATTERN = "*${FontStore.EXTENSION}"

	fun pick(): Path? = MemoryStack.stackPush().use { stack ->
		val patterns = stack.mallocPointer(1)
		patterns.put(stack.UTF8(PATTERN))
		patterns.flip()
		TinyFileDialogs.tinyfd_openFileDialog(TITLE, null as CharSequence?, patterns, DESCRIPTION, false)
			?.takeIf { it.isNotBlank() }
			?.let { Path.of(it) }
	}
}
