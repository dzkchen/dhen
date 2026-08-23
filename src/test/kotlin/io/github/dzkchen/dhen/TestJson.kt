package io.github.dzkchen.dhen

import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal fun json(body: String): JsonObject = JsonParser.parseString(body).asJsonObject
