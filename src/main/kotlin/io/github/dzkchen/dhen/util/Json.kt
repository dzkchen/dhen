package io.github.dzkchen.dhen.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal fun JsonObject.obj(member: String): JsonObject? = get(member) as? JsonObject

internal fun JsonObject.array(member: String): JsonArray? = get(member) as? JsonArray

internal fun JsonObject.flag(member: String): Boolean = primitive(member) { isBoolean }?.asBoolean == true

internal fun JsonObject.text(member: String): String? =
	primitive(member) { isString }?.asString?.takeIf(String::isNotBlank)

internal fun JsonObject.number(member: String): Double? = primitive(member) { isNumber }?.asDouble

private inline fun JsonObject.primitive(member: String, kind: JsonPrimitive.() -> Boolean) =
	(get(member) as? JsonPrimitive)?.takeIf(kind)
