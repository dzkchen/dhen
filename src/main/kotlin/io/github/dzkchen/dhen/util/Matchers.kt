package io.github.dzkchen.dhen.util

import java.util.regex.Matcher
import java.util.regex.Pattern

internal fun matcher(pattern: String): ThreadLocal<Matcher> = ThreadLocal.withInitial { Pattern.compile(pattern).matcher("") }
