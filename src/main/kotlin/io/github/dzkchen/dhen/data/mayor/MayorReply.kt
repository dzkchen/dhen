package io.github.dzkchen.dhen.data.mayor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonParser

class Mayor internal constructor(val name: String, val perks: Set<String>)

internal class MayorReply(
	val lastUpdated: Long,
	val mayor: Mayor,
	val minister: String?,
	val ministerPerk: String?
) {
	companion object {
		fun parse(body: String): MayorReply? {
			val json = JsonParser.parseString(body) as? JsonObject ?: return null
			if (json.flag("success") != true) return null
			val mayor = json.obj("mayor") ?: return null
			val name = mayor.text("name") ?: return null
			val perks = perkNames(mayor.get("perks") as? JsonArray) ?: return null
			val minister = mayor.obj("minister")
			return MayorReply(
				lastUpdated = json.number("lastUpdated"),
				mayor = Mayor(name, perks),
				minister = minister?.text("name"),
				ministerPerk = minister?.obj("perk")?.text("name")
			)
		}

		private fun perkNames(perks: JsonArray?): Set<String>? {
			val names = LinkedHashSet<String>()
			for (perk in perks ?: return names) names += (perk as? JsonObject)?.text("name") ?: return null
			return names
		}

		private fun JsonObject.obj(member: String): JsonObject? = get(member) as? JsonObject

		private fun JsonObject.flag(member: String): Boolean? = primitive(member) { isBoolean }?.asBoolean

		private fun JsonObject.number(member: String): Long = primitive(member) { isNumber }?.asLong ?: 0L

		private fun JsonObject.text(member: String): String? =
			primitive(member) { isString }?.asString?.takeIf(String::isNotEmpty)

		private inline fun JsonObject.primitive(member: String, kind: JsonPrimitive.() -> Boolean) =
			(get(member) as? JsonPrimitive)?.takeIf(kind)
	}
}
