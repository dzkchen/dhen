package io.github.dzkchen.dhen.data.profile

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.LevelLadder
import io.github.dzkchen.dhen.util.flagOrNull
import io.github.dzkchen.dhen.util.keys
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.numericInts
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text

private const val TOGGLE = "toggle_"
private const val EXPERIENCE = "experience"
private const val NODES = "nodes"
private const val SELECTED_ABILITY = "selected_ability"
private const val SELECTED_SLOT = "selected_skill_tree_slot"

enum class SkillTreeFamily(internal val apiKey: String, internal val ladder: String) {
	HEART_OF_THE_MOUNTAIN("mining", "HOTM"),
	HEART_OF_THE_FOREST("foraging", "HOTF")
}

class SkillTreeSlot internal constructor(
	val perks: Map<String, Int>,
	val disabledPerks: Set<String>,
	val selectedAbility: String?
)

class SkillTree internal constructor(
	val experience: Double,
	val level: Int,
	val maxLevel: Int,
	val selectedSlot: Int,
	val slots: Map<Int, SkillTreeSlot>
) {
	val selected: SkillTreeSlot = slots.getValue(selectedSlot)

	internal fun reserve(collected: Double?, spent: (Int) -> Double?): CurrencyReserve = CurrencyReserve(
		collected = collected?.toLong() ?: 0L,
		spentBySlot = SkillTrees.SLOTS.associateWith { spent(it)?.toLong() ?: 0L },
		selectedSlot = selectedSlot
	)
}

class CurrencyReserve internal constructor(
	val collected: Long,
	val spentBySlot: Map<Int, Long>,
	selectedSlot: Int
) {
	val spent: Long = spentBySlot[selectedSlot] ?: 0L

	val available: Long = collected - spent
}

internal object SkillTrees {
	val SLOTS = 1..5

	private val FAMILY_SECTIONS = listOf(EXPERIENCE, NODES, SELECTED_ABILITY, SELECTED_SLOT)

	fun of(tree: JsonObject?, family: SkillTreeFamily): SkillTree {
		val experience = tree?.obj(EXPERIENCE)?.number(family.apiKey) ?: 0.0
		val nodes = tree?.obj(NODES)
		val abilities = tree?.obj(SELECTED_ABILITY)
		val constants = ItemRepo.constants
		return SkillTree(
			experience = experience,
			level = constants.level(LevelLadder.SKILL_TREE, experience, family.ladder),
			maxLevel = constants.maxLevel(LevelLadder.SKILL_TREE, family.ladder),
			selectedSlot = selectedSlot(tree, family),
			slots = SLOTS.associateWith { slotOf(nodes, abilities, family, it) }
		)
	}

	fun inSlot(key: String, slot: Int): String = if (slot > SLOTS.first) "${key}_$slot" else key

	fun mentions(tree: JsonObject?, family: SkillTreeFamily): Boolean {
		val named = SLOTS.mapTo(HashSet()) { inSlot(family.apiKey, it) }
		return FAMILY_SECTIONS.any { section -> tree?.obj(section).keys().any(named::contains) }
	}

	private fun selectedSlot(tree: JsonObject?, family: SkillTreeFamily): Int =
		tree?.obj(SELECTED_SLOT)?.number(family.apiKey)?.toInt()?.takeIf { it in SLOTS } ?: SLOTS.first

	private fun slotOf(nodes: JsonObject?, abilities: JsonObject?, family: SkillTreeFamily, slot: Int): SkillTreeSlot {
		val key = inSlot(family.apiKey, slot)
		val perks = nodes?.obj(key)
		return SkillTreeSlot(
			perks = perks.numericInts().filterKeys { !it.startsWith(TOGGLE) },
			disabledPerks = disabledPerks(perks),
			selectedAbility = abilities?.text(key)
		)
	}

	private fun disabledPerks(nodes: JsonObject?): Set<String> {
		if (nodes == null) return emptySet()
		return nodes.keySet().asSequence()
			.filter { it.startsWith(TOGGLE) && nodes.flagOrNull(it) == false }
			.mapTo(LinkedHashSet()) { it.removePrefix(TOGGLE) }
	}
}
