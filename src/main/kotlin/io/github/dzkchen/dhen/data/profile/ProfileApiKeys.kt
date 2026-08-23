package io.github.dzkchen.dhen.data.profile

import java.util.Locale

internal fun Enum<*>.lowercaseApiKey(): String = name.lowercase(Locale.ROOT)
