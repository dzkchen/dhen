package io.github.dzkchen.dhen.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal fun JsonObject?.keys(): Set<String> = this?.keySet() ?: emptySet()

internal fun JsonObject.obj(member: String): JsonObject? = get(member) as? JsonObject

internal fun JsonObject.array(member: String): JsonArray? = get(member) as? JsonArray

internal fun JsonObject.flag(member: String): Boolean = get(member).flagOrNull() == true

internal fun JsonObject.text(member: String): String? = get(member).textOrNull()

internal fun JsonObject.number(member: String): Double? = get(member).numberOrNull()

internal fun JsonObject.flagOrNull(member: String): Boolean? = get(member).flagOrNull()

internal fun JsonObject?.ints(fallback: Int = 0): Map<String, Int> =
	keys().associateWith { this?.number(it)?.toInt() ?: fallback }

internal fun JsonElement?.flagOrNull(): Boolean? = primitive { isBoolean }?.asBoolean

internal fun JsonElement?.textOrNull(): String? =
	primitive { isString }?.asString?.takeIf(String::isNotBlank)

internal fun JsonElement?.numberOrNull(): Double? =
	primitive { isNumber }?.asDouble?.takeIf(Double::isFinite)

private inline fun JsonElement?.primitive(kind: JsonPrimitive.() -> Boolean) =
	(this as? JsonPrimitive)?.takeIf(kind)
