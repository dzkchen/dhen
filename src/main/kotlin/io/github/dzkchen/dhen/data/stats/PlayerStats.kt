package io.github.dzkchen.dhen.data.stats

enum class ActionBarSegment {
	HEALTH,
	DEFENSE,
	MANA,
	OVERFLOW_MANA,
	VITALITY,
	SECRETS,
	ARMOR_STACKS,
	TERMINATOR_STACKS
}

object PlayerStats {
	private val stripped = BooleanArray(ActionBarSegment.entries.size)

	var health: Int = 0
		internal set

	var maxHealth: Int = 0
		internal set

	var defense: Int = 0
		internal set

	var mana: Int = 0
		internal set

	var maxMana: Int = 0
		internal set

	var overflowMana: Int = 0
		internal set

	var vitality: Int = 0
		internal set

	var maxVitality: Int = 0
		internal set

	var vitalityShown: Boolean = false
		internal set

	var speed: Int = 0
		internal set

	var netherArmorStacks: Int = 0
		internal set

	var stackSymbol: String = ""
		internal set

	var salvation: Int = 0
		internal set

	var secrets: Int = 0
		internal set

	var maxSecrets: Int = 0
		internal set

	val effectiveHp: Int get() = (health.toLong() * (100L + defense) / 100L).toInt()

	internal var anyHidden: Boolean = false
		private set

	fun hidden(segment: ActionBarSegment): Boolean = stripped[segment.ordinal]

	internal fun hide(segment: ActionBarSegment, hidden: Boolean) {
		stripped[segment.ordinal] = hidden
		anyHidden = stripped.any { it }
	}

	internal fun reset() {
		health = 0
		maxHealth = 0
		defense = 0
		mana = 0
		maxMana = 0
		overflowMana = 0
		vitality = 0
		maxVitality = 0
		vitalityShown = false
		speed = 0
		netherArmorStacks = 0
		stackSymbol = ""
		salvation = 0
		secrets = 0
		maxSecrets = 0
	}
}
