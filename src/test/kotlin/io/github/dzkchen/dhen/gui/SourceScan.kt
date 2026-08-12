package io.github.dzkchen.dhen.gui

import java.io.File

internal object SourceScan {
	fun files(root: File, extensions: Set<String>): List<File> =
		root.walkTopDown().filter { it.isFile && it.extension in extensions }.toList()

	fun offenders(files: List<File>, exempt: String, pattern: Regex): List<String> =
		files.asSequence()
			.filter { it.name != exempt }
			.flatMap { file ->
				file.readLines().asSequence().mapIndexedNotNull { index, line ->
					if (pattern.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
				}
			}
			.toList()
}
