package io.github.dzkchen.dhen.gui

import java.io.File

internal object SourceScan {
	val MAIN = File("src/main")

	private val EXTENSIONS = setOf("kt", "java")

	fun files(root: File = MAIN, extensions: Set<String> = EXTENSIONS): List<File> =
		root.walkTopDown().filter { it.isFile && it.extension in extensions }.toList()

	fun offenders(files: List<File>, exempt: String, pattern: Regex): List<String> =
		offenders(files, setOf(exempt), pattern)

	fun offenders(files: List<File>, exempt: Set<String>, pattern: Regex): List<String> =
		files.asSequence()
			.filter { file -> exempt.none { seam -> file.invariantSeparatorsPath.endsWith("/$seam") } }
			.flatMap { file ->
				file.readLines().asSequence().mapIndexedNotNull { index, line ->
					if (pattern.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
				}
			}
			.toList()
}
