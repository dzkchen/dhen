package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.PetInfo
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.pet.PetProgress
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.editingHud
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.round

internal class PetExpTooltip {
	private val items = SkyBlockItems.memo(1)
	private var cachedPet: PetInfo? = null
	private var cachedDragonEgg = false
	private var cachedMaxLevel = 0
	private var cachedRepoCommit: String? = null
	private var cachedLines: List<Component> = emptyList()

	fun add(event: TooltipEvent, showAlways: Boolean, dragonEgg: Boolean, shiftDown: Boolean) {
		if (!SkyBlockLocation.inSkyBlock || !showAlways && !shiftDown) return
		val insertion = insertionIndex(event.lines)
		if (insertion < 0) return
		val pet = items.of(0, event.stack).pet ?: return
		val lines = lines(pet, dragonEgg)
		if (lines.isEmpty()) return
		event.edit().addAll(insertion.coerceAtMost(event.lines.size), lines)
	}

	fun reset() {
		cachedPet = null
		cachedLines = emptyList()
	}

	private fun lines(pet: PetInfo, dragonEgg: Boolean): List<Component> {
		val repoCommit = ItemRepo.commit
		if (
			pet === cachedPet && dragonEgg == cachedDragonEgg && repoCommit == cachedRepoCommit
		) return cachedLines
		return cache(pet, dragonEgg, PetProgress.of(pet).maxLevel, repoCommit)
	}

	internal fun lines(pet: PetInfo, dragonEgg: Boolean, maxLevel: Int): List<Component> =
		cache(pet, dragonEgg, maxLevel, ItemRepo.commit)

	private fun cache(pet: PetInfo, dragonEgg: Boolean, maxLevel: Int, repoCommit: String?): List<Component> {
		if (
			pet === cachedPet && dragonEgg == cachedDragonEgg && maxLevel == cachedMaxLevel &&
			repoCommit == cachedRepoCommit
		) return cachedLines
		cachedPet = pet
		cachedDragonEgg = dragonEgg
		cachedMaxLevel = maxLevel
		cachedRepoCommit = repoCommit
		cachedLines = buildLines(pet, dragonEgg, maxLevel)
		return cachedLines
	}

	internal fun insertionIndex(lines: List<Component>): Int {
		for (index in lines.indices) if (lines[index].string.contains(MAX_LEVEL_MARKER)) return index + 2
		for (index in lines.indices) if (lines[index].string.contains(PROGRESS_MARKER)) return index + 3
		return -1
	}

	private fun buildLines(pet: PetInfo, dragonEgg: Boolean, petMaxLevel: Int): List<Component> {
		val roundedExp = round(pet.exp * 10.0) / 10.0
		val level200 = petMaxLevel == DRAGON_MAX_LEVEL && (!dragonEgg || roundedExp >= LEVEL_100_LEGENDARY)
		val maxLevel = if (level200) DRAGON_MAX_LEVEL else MAX_LEVEL
		val maxXp = when {
			level200 -> LEVEL_200_LEGENDARY
			pet.type == BINGO -> LEVEL_100_COMMON
			else -> LEVEL_100_LEGENDARY
		}
		val progress = roundedExp / maxXp
		if (progress >= 1.0) return emptyList()
		val levelColor = if (ItemRarity.of(pet) < ItemRarity.LEGENDARY) ChatFormatting.GOLD else ChatFormatting.GRAY
		val heading = Component.literal("Progress to ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal("Level $maxLevel").withStyle(levelColor))
			.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(String.format(Locale.ROOT, "%.2f%%", progress * 100.0)).withStyle(ChatFormatting.YELLOW))
		val filled = ceil(progress.coerceAtLeast(0.0) * BAR_STEPS).toInt().coerceAtMost(BAR_STEPS)
		val bar = Component.empty()
		if (filled > 0) bar.append(Component.literal(" ".repeat(filled)).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD, ChatFormatting.STRIKETHROUGH))
		if (filled < BAR_SEGMENTS) {
			bar.append(Component.literal(" ".repeat(BAR_SEGMENTS - filled)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD, ChatFormatting.STRIKETHROUGH))
		}
		bar.append(Component.literal(" ${separated(roundedExp)}").withStyle(ChatFormatting.YELLOW))
		bar.append(Component.literal("/").withStyle(ChatFormatting.GOLD))
		bar.append(Component.literal(short(maxXp)).withStyle(ChatFormatting.YELLOW))
		return listOf(heading, bar, Component.literal(" "))
	}

	private fun separated(value: Double): String = String.format(Locale.US, "%,.1f", value).removeSuffix(".0")

	private fun short(value: Int): String {
		val divisor = when {
			value >= 1_000_000_000 -> 1_000_000_000
			value >= 1_000_000 -> 1_000_000
			value >= 1_000 -> 1_000
			else -> return value.toString()
		}
		val suffix = when (divisor) {
			1_000_000_000 -> "B"
			1_000_000 -> "M"
			else -> "k"
		}
		val tenths = value / (divisor / 10)
		return if (tenths % 10 == 0) "${tenths / 10}$suffix" else "${tenths / 10.0}$suffix"
	}

	private companion object {
		const val LEVEL_100_COMMON = 5_624_785
		const val LEVEL_100_LEGENDARY = 25_353_230
		const val LEVEL_200_LEGENDARY = 210_255_385
		const val MAX_LEVEL = 100
		const val DRAGON_MAX_LEVEL = 200
		const val BINGO = "BINGO"
		const val BAR_STEPS = 24
		const val BAR_SEGMENTS = BAR_STEPS + 1
		const val MAX_LEVEL_MARKER = "MAX LEVEL"
		const val PROGRESS_MARKER = "Progress to Level"
	}
}

internal class GeorgeHelper {
	private var stack: ItemStack = ItemStack.EMPTY
	private var fingerprint = Int.MIN_VALUE
	var lines: Array<String> = EMPTY_LINES
		private set

	fun ready(event: ContainerReadyEvent) = observe(event.title.string, event.stacks)

	fun updated(event: ContainerUpdatedEvent) = observe(event.title.string, event.stacks)

	fun closed(event: ContainerClosedEvent) {
		if (!event.reopening) reset()
	}

	fun tick(fetchOtherTiers: Boolean) {
		if (stack.isEmpty) return
		val nextFingerprint = Prices.revision * 31 + if (fetchOtherTiers) 1 else 0
		if (nextFingerprint == fingerprint) return
		fingerprint = nextFingerprint
		lines = buildLines(SkyBlockItems.lore(stack), fetchOtherTiers, Prices::lowestBin)
	}

	fun reset() {
		stack = ItemStack.EMPTY
		fingerprint = Int.MIN_VALUE
		lines = EMPTY_LINES
	}

	internal fun buildLines(lore: List<Component>, fetchOtherTiers: Boolean, price: (String) -> Double?): Array<String> {
		val result = ArrayList<String>()
		result += "§dTaming 60 Helper"
		var total = 0.0
		for (line in lore) {
			val wanted = wantedPet(withoutCodes(line.string)) ?: continue
			val priced = cheapest(wanted, fetchOtherTiers, price)
			if (priced.price != null) total += priced.price
			result += displayLine(wanted.name, priced)
		}
		if (result.size == 1) return EMPTY_LINES
		result += "§7Total Cost: §6${coins(total)} coins"
		return result.toTypedArray()
	}

	private fun observe(title: String, stacks: List<ItemStack>) {
		if (withoutCodes(title) != OFFER_PETS || stacks.size <= EGG_SLOT || stacks[EGG_SLOT].isEmpty) {
			reset()
			return
		}
		stack = stacks[EGG_SLOT]
		fingerprint = Int.MIN_VALUE
	}

	private fun wantedPet(line: String): WantedPet? {
		val trimmed = line.trim()
		val space = trimmed.indexOf(' ')
		if (space <= 0 || space == trimmed.lastIndex) return null
		val tier = trimmed.substring(0, space).uppercase(Locale.ROOT)
		val tierIndex = TIERS.indexOf(tier)
		if (tierIndex < 0) return null
		return WantedPet(trimmed.substring(space + 1), tierIndex)
	}

	private fun cheapest(wanted: WantedPet, fetchOtherTiers: Boolean, price: (String) -> Double?): PricedPet {
		var cheapestTier = wanted.tier
		var cheapestPrice = price(marketId(wanted.name, wanted.tier))?.takeIf { it > 0.0 }
		if (fetchOtherTiers) {
			for (tier in maxOf(0, wanted.tier - 1)..minOf(TIERS.lastIndex, wanted.tier + 1)) {
				val candidate = price(marketId(wanted.name, tier))?.takeIf { it > 0.0 } ?: continue
				if (cheapestPrice == null || candidate < cheapestPrice) {
					cheapestTier = tier
					cheapestPrice = candidate
				}
			}
		}
		return PricedPet(cheapestTier, cheapestPrice)
	}

	private fun marketId(name: String, tier: Int): String =
		"PET-${name.uppercase(Locale.ROOT).replace(' ', '_')}-${TIERS[tier]}"

	private fun displayLine(name: String, pet: PricedPet): String {
		val rarity = RARITY_CODES[pet.tier]
		val tier = TIERS[pet.tier].lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)
		val price = pet.price
		return if (price == null) " §7- $rarity$tier $name§7: §cNo price found."
		else " §7- $rarity$tier $name§7: §6${coins(price)} coins"
	}

	private fun coins(value: Double): String = String.format(Locale.US, "%,.0f", value)

	private class WantedPet(val name: String, val tier: Int)

	private class PricedPet(val tier: Int, val price: Double?)

	private companion object {
		const val OFFER_PETS = "Offer Pets"
		const val EGG_SLOT = 41
		val EMPTY_LINES = emptyArray<String>()
		val TIERS = arrayOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
		val RARITY_CODES = arrayOf("§f", "§a", "§9", "§5", "§6", "§d")
	}
}

internal class GeorgeHelperElement(private val helper: GeorgeHelper) : HudElement(
	"George Helper",
	HudAnchor.MIDDLE_LEFT,
	20,
	0
) {
	private var memos: Array<TextMemo> = emptyArray()

	override val hasContent: Boolean
		get() = editingHud() || PetDisplay.georgeHelperSetting.on && SkyBlockLocation.island == Island.HUB && helper.lines.isNotEmpty()

	override fun width(font: Font): Int {
		val shown = shownLines()
		ensureMemos(shown.size)
		var width = 1
		for (index in shown.indices) width = maxOf(width, memos[index].width(font, shown[index]))
		return width
	}

	override fun height(font: Font): Int = shownLines().size * DhenType.lineHeight(font)

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val shown = shownLines()
		ensureMemos(shown.size)
		val lineHeight = DhenType.lineHeight(font)
		for (index in shown.indices) {
			memos[index].shadowed(graphics, font, shown[index], 0, index * lineHeight, DhenPalette.TEXT_ON_WORLD, scale)
		}
	}

	override fun invalidateMeasurement() {
		for (memo in memos) memo.invalidate()
	}

	private fun shownLines(): Array<String> = if (editingHud()) PREVIEW else helper.lines

	private fun ensureMemos(size: Int) {
		if (memos.size != size) memos = Array(size) { DhenType.memo() }
	}

	private companion object {
		val PREVIEW = arrayOf(
			"§dTaming 60 Helper",
			" §7- §6Legendary Black Cat§7: §612,500,000 coins",
			"§7Total Cost: §612,500,000 coins"
		)
	}
}
