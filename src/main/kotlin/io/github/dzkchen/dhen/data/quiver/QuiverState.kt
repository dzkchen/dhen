package io.github.dzkchen.dhen.data.quiver

enum class QuiverArrow(val id: String, val displayName: String) {
	NONE("NONE", "None"),
	SLIME_BALL("SLIME_BALL", "Slime Ball"),
	PRISMARINE_SHARD("PRISMARINE_SHARD", "Prismarine Shard"),
	FLINT("ARROW", "Flint Arrow"),
	REINFORCED_IRON("REINFORCED_IRON_ARROW", "Reinforced Iron Arrow"),
	GOLD_TIPPED("GOLD_TIPPED_ARROW", "Gold-tipped Arrow"),
	REDSTONE_TIPPED("REDSTONE_TIPPED_ARROW", "Redstone-tipped Arrow"),
	EMERALD_TIPPED("EMERALD_TIPPED_ARROW", "Emerald-tipped Arrow"),
	BOUNCY("BOUNCY_ARROW", "Bouncy Arrow"),
	ICY("ICY_ARROW", "Icy Arrow"),
	ARMORSHRED("ARMORSHRED_ARROW", "Armorshred Arrow"),
	EXPLOSIVE("EXPLOSIVE_ARROW", "Explosive Arrow"),
	GLUE("GLUE_ARROW", "Glue Arrow"),
	NANSORB("NANSORB_ARROW", "Nansorb Arrow"),
	MAGMA("MAGMA_ARROW", "Magma Arrow");

	companion object {
		fun byId(id: String): QuiverArrow? = entries.firstOrNull { it.id == id }

		fun byName(name: String): QuiverArrow? = entries.firstOrNull { it.displayName == name }
	}
}

object QuiverState {
	const val MAX_AMOUNT = 2880

	private val amounts = IntArray(QuiverArrow.entries.size)

	var currentArrow: QuiverArrow? = null
		private set

	val currentAmount: Int
		get() = currentArrow?.let { amounts[it.ordinal] } ?: 0

	fun amount(arrow: QuiverArrow): Int = amounts[arrow.ordinal]

	internal fun select(arrow: QuiverArrow): Boolean {
		if (currentArrow == arrow) return false
		currentArrow = arrow
		return true
	}

	internal fun setAmount(arrow: QuiverArrow, amount: Int): Boolean {
		val next = amount.coerceAtLeast(0)
		if (amounts[arrow.ordinal] == next) return false
		amounts[arrow.ordinal] = next
		return true
	}

	internal fun add(arrow: QuiverArrow, amount: Int): Boolean =
		setAmount(arrow, amounts[arrow.ordinal] + amount)

	internal fun replaceAmounts(replacement: IntArray): Boolean {
		val previous = currentAmount
		for (index in amounts.indices) amounts[index] = replacement[index]
		return previous != currentAmount
	}

	internal fun clearAmounts(): Boolean {
		var changed = false
		for (index in amounts.indices) {
			changed = changed || amounts[index] != 0
			amounts[index] = 0
		}
		return changed
	}

	internal fun reset(): Boolean {
		val amountsChanged = clearAmounts()
		val changed = currentArrow != null || amountsChanged
		currentArrow = null
		return changed
	}
}
