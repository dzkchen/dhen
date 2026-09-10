package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.price.BazaarOrder

internal object CraftingRules {
	private val supercraftedMessage = Regex("§eYou Supercrafted §r§r§r§.(?<item>[^§]+?)\\s*(?:§r§8x(?<amount>[\\d,]+))?§r§e!")

	fun supercrafted(message: String): MatchResult? = supercraftedMessage.matchEntire(message)

	fun quickSlot(title: String, slot: Int): Boolean = when (title) {
		"Craft Item" -> slot == 16 || slot == 25 || slot == 34
		"Quick Crafting" -> slot in 10..44
		else -> false
	}

	fun sign(lines: Array<String>): Boolean = lines.size >= 4 && lines[1] == "^^^^^^" && lines[2] == "Enter amount" && lines[3] == "of crafts"

	fun presets(text: String): List<Int> = text.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { value -> value > 0 } }.distinct()

	fun maximum(owned: Long, used: Long, amount: Long, multiplier: Int): Long? {
		if (owned <= 0 || used <= 0 || amount <= 0 || multiplier <= 0) return null
		val crafts = amount / multiplier
		if (crafts == 0L) return null
		val perCraft = used / crafts
		return if (perCraft == 0L) null else owned / perCraft
	}

	fun blocks(profit: Double, amount: Long, maximum: Long?, threshold: Double, bulk: Double): Boolean =
		profit < -threshold * MILLION || maximum == amount && profit < -bulk * MILLION

	fun cost(orders: List<BazaarOrder>, amount: Long): Double? {
		if (amount <= 0) return null
		var remaining = amount
		var total = 0.0
		for (order in orders) {
			val take = minOf(remaining, order.amount)
			total += take * order.pricePerUnit
			remaining -= take
			if (remaining == 0L) return total
		}
		return null
	}

	private const val MILLION = 1_000_000.0
}
