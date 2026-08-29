package io.github.dzkchen.dhen.util

import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException

internal fun Exception.reason(): String = when (this) {
	is AccessDeniedException -> "your system would not let Dhen write $file"
	is FileSystemException -> reason ?: "$file could not be used"
	else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
