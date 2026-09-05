package io.github.dzkchen.dhen.data.sack

import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.compactNumber
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

internal const val MAGMA_FISH = "MAGMA_FISH"

internal const val UNREPORTED = -1L

internal val GEMSTONE_QUALITIES = arrayOf("ROUGH", "FLAWED", "FINE")

internal val GEMSTONE_MULTIPLIERS = longArrayOf(1L, 80L, 80L * 80L)

internal val RUNE_LEVELS = arrayOf("I", "II", "III")

internal class SackRow(
	val label: String,
	val marketId: String,
	val stack: ItemStack,
	val stored: Long,
	val capacity: Long,
	val full: Boolean,
	val slot: Int,
	val magmafish: Long,
	val parts: LongArray?,
	val partIds: Array<String>?
)

internal object SackMenu {
	private val sackTitle = Pattern.compile("(?:.* Sack|Enchanted .* Sack)").matcher("")
	private val storedLine =
		Pattern.compile("(?:§.(?<level>I{1,3})§7:|§7Stored:) (?<color>§.)(?<stored>[\\d.,kKmMbB]+)§7/(?<total>[\\d.,kKmMbB]+)")
			.matcher("")
	private val gemstoneCount =
		Pattern.compile(" (?:§.)?(?<quality>[A-Za-z]+): §.(?<stored>[\\d.,kKmM]+)(?: §.\\(.*\\))?").matcher("")
	private val gemstoneName = Pattern.compile("(?:(?:§.)?. |§.)(?:(?:Rough|Flawed|Fine) )?(?<gem>[^ ]+) Gemstones?").matcher("")
	private val gemstoneFilter = Pattern.compile("(?:§.)+▶ (?<quality>.*)").matcher("")

	private const val FILTER_SLOT = 41
	private const val FULL_MARKER = "§7Stored: §a"

	fun isSack(title: String): Boolean = sackTitle.reset(title).matches()

	fun isSackOfSacks(title: String): Boolean = title == SACK_OF_SACKS

	fun isTrophySack(title: String): Boolean = title.contains("Trophy Fishing Sack")

	fun isFull(stack: ItemStack): Boolean =
		SkyBlockItems.rawLore(stack).any { legacyCodes(it).startsWith(FULL_MARKER) }

	fun gemstoneFilter(stacks: List<ItemStack>): String? {
		val filter = stacks.getOrNull(FILTER_SLOT) ?: return null
		for (line in SkyBlockItems.rawLore(filter)) {
			if (!gemstoneFilter.reset(legacyCodes(line)).matches()) continue
			val quality = gemstoneFilter.group("quality").uppercase()
			return if (quality in GEMSTONE_QUALITIES) quality else null
		}
		return null
	}

	fun read(title: String, stacks: List<ItemStack>, rows: MutableList<SackRow>) {
		rows.clear()
		val runes = title == "Runes Sack"
		val gemstones = title == "Gemstones Sack"
		val trophies = isTrophySack(title)
		val filter = if (gemstones) gemstoneFilter(stacks) else null
		for (slot in stacks.indices) {
			val stack = stacks[slot]
			if (stack.isEmpty) continue
			when {
				gemstones -> gemstoneRow(stack, slot, filter)?.let(rows::add)
				runes -> runeRow(stack, slot)?.let(rows::add)
				else -> itemRow(stack, slot, trophies)?.let(rows::add)
			}
		}
	}

	private fun itemRow(stack: ItemStack, slot: Int, trophies: Boolean): SackRow? {
		for (line in SkyBlockItems.rawLore(stack)) {
			if (!storedLine.reset(legacyCodes(line)).find()) continue
			val marketId = SkyBlockItems.of(stack).marketId
			if (marketId.isEmpty()) return null
			val stored = compactNumber(storedLine.group("stored")) ?: return null
			val capacity = compactNumber(storedLine.group("total")) ?: 0L
			return SackRow(
				label = withoutCodes(stack.hoverName.string),
				marketId = marketId,
				stack = stack,
				stored = stored,
				capacity = capacity,
				full = storedLine.group("color") == FULL_COLOR,
				slot = slot,
				magmafish = if (trophies) fillet(marketId) * stored else 0L,
				parts = null,
				partIds = null
			)
		}
		return null
	}

	private fun runeRow(stack: ItemStack, slot: Int): SackRow? {
		val levels = LongArray(RUNE_LEVELS.size)
		var total = 0L
		var found = false
		for (line in SkyBlockItems.rawLore(stack)) {
			if (!storedLine.reset(legacyCodes(line)).find()) continue
			val level = RUNE_LEVELS.indexOf(storedLine.group("level") ?: continue)
			if (level < 0) continue
			val stored = compactNumber(storedLine.group("stored")) ?: continue
			levels[level] = stored
			total += stored
			found = true
		}
		if (!found) return null
		val marketId = SkyBlockItems.of(stack).marketId
		return SackRow(
			label = withoutCodes(stack.hoverName.string),
			marketId = marketId,
			stack = stack,
			stored = total,
			capacity = 0L,
			full = false,
			slot = slot,
			magmafish = 0L,
			parts = levels,
			partIds = null
		)
	}

	private fun gemstoneRow(stack: ItemStack, slot: Int, filter: String?): SackRow? {
		if (!gemstoneName.reset(legacyCodes(stack.hoverName)).matches()) return null
		val gem = gemstoneName.group("gem").uppercase()
		val counts = LongArray(GEMSTONE_QUALITIES.size) { UNREPORTED }
		val ids = Array(GEMSTONE_QUALITIES.size) { "${GEMSTONE_QUALITIES[it]}_${gem}_GEM" }
		var stored = 0L
		var found = false
		for (line in SkyBlockItems.rawLore(stack)) {
			if (!gemstoneCount.reset(legacyCodes(line)).matches()) continue
			val label = gemstoneCount.group("quality").uppercase()
			val quality = if (label == AMOUNT_LABEL) filter else label
			val index = GEMSTONE_QUALITIES.indexOf(quality ?: continue)
			if (index < 0) continue
			val amount = compactNumber(gemstoneCount.group("stored")) ?: continue
			counts[index] = amount
			stored += amount * GEMSTONE_MULTIPLIERS[index]
			found = true
		}
		if (!found) return null
		return SackRow(
			label = "${gem.lowercase().replaceFirstChar(Char::uppercaseChar)} Gemstones",
			marketId = ids[0],
			stack = stack,
			stored = stored,
			capacity = 0L,
			full = false,
			slot = slot,
			magmafish = 0L,
			parts = counts,
			partIds = ids
		)
	}

	private fun fillet(marketId: String): Long = ItemRepo.constants.trophyFillet(marketId).toLong()

	private const val SACK_OF_SACKS = "Sack of Sacks"
	private const val FULL_COLOR = "§a"
	private const val AMOUNT_LABEL = "AMOUNT"
}
