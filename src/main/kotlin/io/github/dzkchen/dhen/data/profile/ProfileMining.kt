package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.util.flagOrNull
import io.github.dzkchen.dhen.util.ints
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import java.util.Locale

private const val HEART_OF_THE_MOUNTAIN = "HOTM"
private const val MINING_TREE = "mining"
private const val TOGGLE = "toggle_"

enum class Powder {
	MITHRIL, GEMSTONE, GLACITE;

	internal val apiKey: String = name.lowercase(Locale.ROOT)
}

class PowderReserve internal constructor(val collected: Long, val spent: Long) {
	val available: Long get() = collected - spent
}

class MiningProfile internal constructor(
	val experience: Double,
	val level: Int,
	val maxLevel: Int,
	val powders: Map<Powder, PowderReserve>,
	val perks: Map<String, Int>,
	val disabledPerks: Set<String>,
	val selectedAbility: String?,
	val selectedTree: Int
)

internal object MiningProfiles {
	fun of(member: JsonObject): MiningProfile? {
		val tree = member.obj("skill_tree")
		val core = member.obj("mining_core")
		if (tree == null && core == null) return null
		val slot = tree?.obj("selected_skill_tree_slot")?.number(MINING_TREE)?.toInt() ?: 1
		val slotKey = inSlot(MINING_TREE, slot)
		val nodes = tree?.obj("nodes")?.obj(slotKey)
		val experience = tree?.obj("experience")?.number(MINING_TREE) ?: 0.0
		val constants = ItemRepo.constants
		return MiningProfile(
			experience = experience,
			level = constants.treeLevel(HEART_OF_THE_MOUNTAIN, experience),
			maxLevel = constants.treeMaxLevel(HEART_OF_THE_MOUNTAIN),
			powders = powders(core, slot),
			perks = perks(nodes),
			disabledPerks = disabledPerks(nodes),
			selectedAbility = tree?.obj("selected_ability")?.text(slotKey),
			selectedTree = slot
		)
	}

	private fun inSlot(key: String, slot: Int): String = if (slot > 1) "${key}_$slot" else key

	private fun powders(core: JsonObject?, slot: Int): Map<Powder, PowderReserve> {
		if (core == null) return emptyMap()
		return Powder.entries.associateWith {
			PowderReserve(
				collected = core.number("powder_${it.apiKey}")?.toLong() ?: 0L,
				spent = core.number(inSlot("powder_spent_${it.apiKey}", slot))?.toLong() ?: 0L
			)
		}
	}

	private fun perks(nodes: JsonObject?): Map<String, Int> = nodes.ints().filterKeys { !it.startsWith(TOGGLE) }

	private fun disabledPerks(nodes: JsonObject?): Set<String> {
		if (nodes == null) return emptySet()
		return nodes.keySet().asSequence()
			.filter { it.startsWith(TOGGLE) && nodes.flagOrNull(it) == false }
			.mapTo(LinkedHashSet()) { it.removePrefix(TOGGLE) }
	}
}
