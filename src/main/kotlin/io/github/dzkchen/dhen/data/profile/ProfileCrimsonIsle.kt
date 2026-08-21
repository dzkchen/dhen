package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import java.util.Locale

private const val HIGHEST_WAVE = "highest_wave_"
private const val DOJO_POINTS = "dojo_points_"
private const val DOJO_TIME = "dojo_time_"
private const val NEVER_ATTEMPTED = -1

enum class Faction {
	MAGE, BARBARIAN;

	internal val apiKey: String = name.lowercase(Locale.ROOT) + "s"

	internal companion object {
		private val byApiKey = entries.associateBy(Faction::apiKey)

		fun of(faction: String?): Faction? = byApiKey[faction]
	}
}

class KuudraTier internal constructor(val completions: Int, val highestWave: Int)

class DojoDiscipline internal constructor(val points: Int?, val time: Int?)

class CrimsonIsleProfile internal constructor(
	val faction: Faction?,
	val reputation: Map<Faction, Int>,
	val kuudra: Map<String, KuudraTier>,
	val dojo: Map<String, DojoDiscipline>
)

internal object CrimsonIsleProfiles {
	fun of(member: JsonObject): CrimsonIsleProfile? {
		val isle = member.obj("nether_island_player_data") ?: return null
		return CrimsonIsleProfile(
			faction = Faction.of(isle.text("selected_faction")),
			reputation = Faction.entries.associateWith { reputation(isle, it) },
			kuudra = kuudra(isle.obj("kuudra_completed_tiers")),
			dojo = dojo(isle.obj("dojo"))
		)
	}

	private fun reputation(isle: JsonObject, faction: Faction): Int =
		(isle.number("${faction.apiKey}_reputation")?.toInt() ?: 0).coerceAtLeast(0)

	private fun kuudra(tiers: JsonObject?): Map<String, KuudraTier> =
		tiers.keys().mapTo(LinkedHashSet()) { it.removePrefix(HIGHEST_WAVE) }.associateWith { id ->
			KuudraTier(
				completions = tiers?.number(id)?.toInt() ?: 0,
				highestWave = tiers?.number(HIGHEST_WAVE + id)?.toInt() ?: 0
			)
		}

	private fun dojo(dojo: JsonObject?): Map<String, DojoDiscipline> =
		dojo.keys().mapNotNullTo(LinkedHashSet(), ::discipline).associateWith { id ->
			DojoDiscipline(points = attempted(dojo, DOJO_POINTS + id), time = attempted(dojo, DOJO_TIME + id))
		}

	private fun discipline(key: String): String? = when {
		key.startsWith(DOJO_POINTS) -> key.removePrefix(DOJO_POINTS)
		key.startsWith(DOJO_TIME) -> key.removePrefix(DOJO_TIME)
		else -> null
	}

	private fun attempted(dojo: JsonObject?, key: String): Int? =
		dojo?.number(key)?.toInt()?.takeIf { it != NEVER_ATTEMPTED }
}
