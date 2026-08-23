package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.long
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numericInts
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.texts

private const val UNDISCOVERED = "NOT_FOUND"

enum class Powder {
	MITHRIL, GEMSTONE, GLACITE;

	internal val apiKey: String = lowercaseApiKey()
}

class Crystal internal constructor(val state: String, val totalPlaced: Int, val totalFound: Int)

class ForgeSlot internal constructor(
	val type: String?,
	val id: String?,
	val startedAt: Long,
	val notified: Boolean
)

class Glacite internal constructor(
	val fossilsDonated: Set<String>,
	val corpsesLooted: Map<String, Int>,
	val mineshaftsEntered: Int
)

class MiningProfile internal constructor(
	val tree: SkillTree,
	val powders: Map<Powder, CurrencyReserve>,
	val crystals: Map<String, Crystal>,
	val forge: Map<Int, ForgeSlot>,
	val glacite: Glacite
)

internal object MiningProfiles {
	private val FAMILY = SkillTreeFamily.HEART_OF_THE_MOUNTAIN

	fun of(member: JsonObject): MiningProfile? {
		val tree = member.obj("skill_tree")
		val core = member.obj("mining_core")
		val glaciteData = member.obj("glacite_player_data")
		val processes = member.obj("forge")?.obj("forge_processes")?.obj("forge_1")
		if (core == null && glaciteData == null && processes == null && !SkillTrees.mentions(tree, FAMILY))
			return null
		val heart = SkillTrees.of(tree, FAMILY)
		return MiningProfile(
			tree = heart,
			powders = powders(core, heart),
			crystals = crystals(core?.obj("crystals")),
			forge = forgeSlots(processes),
			glacite = glacite(glaciteData)
		)
	}

	private fun powders(core: JsonObject?, tree: SkillTree): Map<Powder, CurrencyReserve> =
		Powder.entries.associateWith { powder ->
			val spent = "powder_spent_${powder.apiKey}"
			tree.reserve(core?.number("powder_${powder.apiKey}")) { slot ->
				core?.number(SkillTrees.inSlot(spent, slot))
			}
		}

	private fun crystals(crystals: JsonObject?): Map<String, Crystal> = crystals.keys().mapNotNull { id ->
		crystals?.obj(id)?.let {
			id to Crystal(
				state = it.text("state") ?: UNDISCOVERED,
				totalPlaced = it.int("total_placed"),
				totalFound = it.int("total_found")
			)
		}
	}.toMap()

	private fun forgeSlots(processes: JsonObject?): Map<Int, ForgeSlot> = processes.keys().mapNotNull { key ->
		val slot = key.toIntOrNull() ?: return@mapNotNull null
		processes?.obj(key)?.let {
			slot to ForgeSlot(
				type = it.text("type"),
				id = it.text("id"),
				startedAt = it.long("startTime"),
				notified = it.flag("notified")
			)
		}
	}.toMap()

	private fun glacite(data: JsonObject?): Glacite = Glacite(
		fossilsDonated = data?.array("fossils_donated").texts().toSet(),
		corpsesLooted = data?.obj("corpses_looted").numericInts(),
		mineshaftsEntered = data.int("mineshafts_entered")
	)
}
