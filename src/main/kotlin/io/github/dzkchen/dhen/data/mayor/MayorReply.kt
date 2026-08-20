package io.github.dzkchen.dhen.data.mayor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text

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
			if (!json.flag("success")) return null
			val mayor = json.obj("mayor") ?: return null
			val name = mayor.text("name") ?: return null
			val perks = perkNames(mayor.array("perks")) ?: return null
			val minister = mayor.obj("minister")
			return MayorReply(
				lastUpdated = json.number("lastUpdated")?.toLong() ?: 0L,
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
	}
}
