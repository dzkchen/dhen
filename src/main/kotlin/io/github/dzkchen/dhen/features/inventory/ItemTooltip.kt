package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerScrollEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.input.controlHeld
import io.github.dzkchen.dhen.input.keyHeld
import io.github.dzkchen.dhen.input.shiftHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.countdown
import io.github.dzkchen.dhen.util.formatted
import io.github.dzkchen.dhen.util.grouped
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ItemTooltip : Module(
	name = "Item Tooltip",
	category = Category.INVENTORY,
	description = "Adds price and quality lines to item tooltips and lets you scroll and scale them."
) {
	internal val showPricesSetting = BooleanSetting(
		"Item Prices",
		description = "Adds Bazaar or lowest-BIN price lines to SkyBlock item tooltips."
	)

	internal val npcSellSetting = BooleanSetting(
		"NPC Sell Price",
		description = "Adds the price an NPC pays for the item."
	).withDependency { showPricesSetting.on }

	internal val spreadSetting = BooleanSetting(
		"Bazaar Spread",
		description = "Adds the gap between the Bazaar buy and sell price."
	).withDependency { showPricesSetting.on }

	internal val roundPricesSetting = BooleanSetting(
		"Round Prices",
		description = "Shortens millions and billions to 1.2M and 1.2B instead of writing them out."
	).withDependency { showPricesSetting.on }

	internal val stackPriceSetting = KeybindSetting(
		"Current Amount Price",
		GLFW.GLFW_KEY_LEFT_SHIFT,
		"Hold to price the whole stack you are hovering."
	).withDependency { showPricesSetting.on }

	internal val fullStackPriceSetting = KeybindSetting(
		"Full Stack Price",
		GLFW.GLFW_KEY_LEFT_ALT,
		"Hold to price a full stack of 64."
	).withDependency { showPricesSetting.on }

	internal val showQualitySetting = BooleanSetting(
		"Item Quality",
		description = "Adds a dungeon item's stat bonus and the floor it came from."
	)

	internal val itemAgeSetting = BooleanSetting(
		"Item Age",
		description = "Adds how long ago you got the item, and the date you got it."
	)

	internal val hideGearScoreSetting = BooleanSetting(
		"Hide Gear Score",
		description = "Removes Hypixel's Gear Score line."
	)

	internal val hideVanillaEnchantsSetting = BooleanSetting(
		"Hide Vanilla Enchants",
		description = "Removes the leftover Aqua Affinity and Depth Strider lines Hypixel leaves on SkyBlock items."
	)

	internal val clampSetting = BooleanSetting(
		"Keep Tooltips On Screen",
		description = "Wraps long tooltip lines and nudges a tooltip back inside the screen edge, " +
			"unless Scrollable Tooltips is on and you are placing it yourself."
	)

	internal val scrollableSetting = BooleanSetting(
		"Scrollable Tooltips",
		description = "Scroll to move a tooltip, hold shift to move it sideways, hold control to resize it."
	)

	internal val scaleSetting = NumberSetting(
		"Tooltip Scale",
		DEFAULT_SCALE,
		MIN_SCALE,
		MAX_SCALE,
		description = "The size every tooltip is drawn at, as a percentage."
	).withDependency { scrollableSetting.on }

	internal val scrollSpeedSetting = NumberSetting(
		"Scroll Speed",
		DEFAULT_SPEED,
		MIN_SPEED,
		MAX_SPEED,
		description = "How far one notch of the wheel moves the tooltip."
	).withDependency { scrollableSetting.on }

	internal val scaleSpeedSetting = NumberSetting(
		"Scale Speed",
		DEFAULT_SPEED,
		MIN_SPEED,
		MAX_SPEED,
		description = "How much one notch of the wheel resizes the tooltip."
	).withDependency { scrollableSetting.on }

	internal var scrollX = 0f
		private set
	internal var scrollY = 0f
		private set
	internal var scaleOverride = 0f
		private set

	private var hoveredSlot = NO_SLOT
	private val priceHold = RequirementHold(Prices::active, Prices::require)
	private var cachedStack: ItemStack? = null
	private var cachedFingerprint = 0
	private var strippedStack: ItemStack? = null
	private var strippedKey = 0
	private var stripCuts = IntArray(STRIP_ROOM)
	private var stripCount = 0
	private var cachedLines: List<Component> = emptyList()
	private var agedStamp = 0L
	private var agedSecond = 0L
	private var agedLine: Component? = null

	init {
		for (
			setting in listOf(
				showPricesSetting,
				npcSellSetting,
				spreadSetting,
				roundPricesSetting,
				stackPriceSetting,
				fullStackPriceSetting,
				showQualitySetting,
				itemAgeSetting,
				hideGearScoreSetting,
				hideVanillaEnchantsSetting,
				clampSetting,
				scrollableSetting,
				scaleSetting,
				scrollSpeedSetting,
				scaleSpeedSetting
			)
		) registerSetting(setting)

		on<TooltipEvent> { event ->
			slotChanged(event.hoveredSlot.index)
			if (!SkyBlockLocation.inSkyBlock) return@on
			if (shaping()) shape(event.edit(), event.stack)
			val lines = lines(event.stack)
			if (lines.isNotEmpty()) event.edit().addAll(lines)
		}
		on<ContainerScrollEvent> { event ->
			if (!scrollableSetting.on) return@on
			val slot = event.hoveredSlot ?: return@on
			if (slot.item.isEmpty) return@on
			applyScroll(event.scrollY, shiftHeld(), controlHeld())
			event.cancelled = true
		}
		on<GuiCloseEvent> {
			hoveredSlot = NO_SLOT
			resetScroll()
		}
		on<ClientTickEvent.End> { ensurePrices() }
	}

	@JvmStatic
	fun scrolling(): Boolean = enabled && scrollableSetting.on

	@JvmStatic
	fun clamping(): Boolean = enabled && clampSetting.on

	@JvmStatic
	fun transformTooltip(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
		val pose = graphics.pose()
		val scale = scale()
		pose.translate(x.toFloat(), y.toFloat())
		pose.scale(scale, scale)
		pose.translate(scrollX, scrollY)
		pose.translate(-x.toFloat(), -y.toFloat())
	}

	internal fun scale(): Float =
		(scaleSetting.amount.toFloat() / PERCENT + scaleOverride).coerceIn(MIN_ZOOM, MAX_ZOOM)

	internal fun applyScroll(amount: Double, shift: Boolean, control: Boolean) {
		val moved = (amount * scrollSpeedSetting.amount).toFloat()
		when {
			shift && !control -> scrollX -= moved
			control && !shift -> {
				val base = scaleSetting.amount.toFloat() / PERCENT
				val next = (base + scaleOverride + (amount / PERCENT).toFloat() * scaleSpeedSetting.amount.toFloat())
					.coerceIn(MIN_ZOOM, MAX_ZOOM)
				scaleOverride = next - base
			}

			else -> scrollY += moved
		}
	}

	internal fun resetScroll() {
		scrollX = 0f
		scrollY = 0f
		scaleOverride = 0f
	}

	internal fun slotChanged(index: Int) {
		if (index == hoveredSlot) return
		hoveredSlot = index
		resetScroll()
	}

	internal fun multiplier(count: Int, stackHeld: Boolean, fullStackHeld: Boolean): Int = when {
		fullStackHeld && stackHeld -> if (count <= 1) FULL_STACK * FULL_STACK else count * FULL_STACK
		fullStackHeld -> FULL_STACK
		stackHeld -> if (count <= 1) FULL_STACK else count
		else -> 1
	}

	internal fun qualityLine(item: SkyBlockItem): String? {
		val boost = item.baseStatBoost
		if (boost <= 0) return null
		val requirement = item.dungeonSkillRequirement
		val floor = item.dungeonFloor
		val origin = when {
			requirement.isEmpty() && floor > 0 -> "§aE"
			requirement.isEmpty() -> "§bF$floor"
			else -> {
				val dungeon = requirement.substringBefore(':')
				val level = requirement.substringAfter(':', "").toIntOrNull() ?: 0
				if (dungeon == CATACOMBS) {
					if (level - floor > MASTER_GAP) "§4M${floor - MASTER_OFFSET}" else "§aF$floor"
				} else {
					"§b$dungeon $floor"
				}
			}
		}
		val tint = when {
			boost <= POOR -> "§c"
			boost <= FAIR -> "§e"
			boost <= GOOD -> "§a"
			else -> "§b"
		}
		return "§6Quality Bonus: $tint+$boost% §7($origin§7)"
	}

	internal fun priceLine(label: String, unit: Double, multiplier: Int): String? {
		if (unit <= 0.0) return null
		val total = unit * multiplier
		val detail = if (multiplier > 1) " §8(${grouped(multiplier.toLong())}x ${coins(unit)})" else ""
		return "§e$label: §6${coins(total)}$detail"
	}

	internal fun coins(value: Double): String = when {
		!roundPricesSetting.on -> formatted(value.toLong())
		value >= BILLION -> String.format(Locale.US, "%.1fB", value / BILLION)
		value >= MILLION -> String.format(Locale.US, "%.1fM", value / MILLION)
		else -> formatted(value.toLong())
	}

	internal fun ageLine(stamp: Long, now: Long): String =
		"§7Age: §c${countdown(now - stamp)} §8(${AGE_DATE.format(Instant.ofEpochMilli(stamp))})"

	internal fun vanillaEnchant(plain: String): Boolean =
		plain.startsWith(AQUA_AFFINITY) || plain.startsWith(DEPTH_STRIDER)

	internal fun stripped(plain: String, gearScore: Boolean, greyEnchant: Boolean): Boolean =
		greyEnchant || gearScore && plain.startsWith(GEAR_SCORE)

	internal fun shaping(): Boolean =
		itemAgeSetting.on || hideGearScoreSetting.on || hideVanillaEnchantsSetting.on

	internal fun shape(lines: MutableList<Component>, stack: ItemStack) {
		strip(lines, stack)
		age(lines, stack)
	}

	private fun strip(lines: MutableList<Component>, stack: ItemStack) {
		val gearScore = hideGearScoreSetting.on
		val enchants = hideVanillaEnchantsSetting.on
		if (!gearScore && !enchants) return
		val key = flag(gearScore) + flag(enchants) * 2 + lines.size * 4
		if (stack !== strippedStack || key != strippedKey) {
			strippedStack = stack
			strippedKey = key
			findStripped(lines, gearScore, enchants)
		}
		for (index in 0 until stripCount) lines.removeAt(stripCuts[index])
	}

	private fun findStripped(lines: List<Component>, gearScore: Boolean, enchants: Boolean) {
		stripCount = 0
		for (index in lines.indices.reversed()) {
			val line = lines[index]
			val plain = withoutCodes(line.string)
			val greyEnchant = enchants && vanillaEnchant(plain) && legacyCodes(line).contains(GREY_CODE)
			if (!stripped(plain, gearScore, greyEnchant)) continue
			if (stripCount == stripCuts.size) stripCuts = stripCuts.copyOf(stripCount * 2)
			stripCuts[stripCount++] = index
		}
	}

	private fun age(lines: MutableList<Component>, stack: ItemStack) {
		if (!itemAgeSetting.on) return
		val stamp = SkyBlockItems.of(stack).timestamp
		if (stamp <= 0L) return
		val now = System.currentTimeMillis()
		if (now <= stamp) return
		val second = now / MILLIS_PER_SECOND
		if (stamp != agedStamp || second != agedSecond) {
			agedStamp = stamp
			agedSecond = second
			agedLine = Component.literal(ageLine(stamp, now))
		}
		lines.add(minOf(AGE_INDEX, lines.size), agedLine ?: return)
	}

	override fun onDisabled() {
		resetScroll()
		hoveredSlot = NO_SLOT
		cachedStack = null
		cachedLines = emptyList()
		agedLine = null
		TooltipShape.forget()
		priceHold.release()
	}

	internal fun decorated(stack: ItemStack): List<Component> {
		val vanilla = Screen.getTooltipFromItem(Minecraft.getInstance(), stack)
		if (!enabled || !SkyBlockLocation.inSkyBlock) return vanilla
		val extra = lines(stack)
		if (!shaping() && extra.isEmpty()) return vanilla
		val shaped = ArrayList(vanilla)
		if (shaping()) shape(shaped, stack)
		shaped += extra
		return shaped
	}

	private fun lines(stack: ItemStack): List<Component> {
		val multiplier = multiplier(stack.count, keyHeld(stackPriceSetting.code), keyHeld(fullStackPriceSetting.code))
		val fingerprint = fingerprint(multiplier)
		if (stack === cachedStack && fingerprint == cachedFingerprint) return cachedLines
		cachedStack = stack
		cachedFingerprint = fingerprint
		cachedLines = build(stack, multiplier)
		return cachedLines
	}

	private fun build(stack: ItemStack, multiplier: Int): List<Component> {
		val item = SkyBlockItems.of(stack)
		val written = ArrayList<Component>(MAX_LINES)
		if (showQualitySetting.on) qualityLine(item)?.let { written += Component.literal(it) }
		if (!showPricesSetting.on) return written
		val market = item.marketId
		val product = Prices.product(market)
		if (product != null) {
			priceLine("Bazaar Buy", product.instantBuy, multiplier)?.let { written += Component.literal(it) }
			priceLine("Bazaar Sell", product.instantSell, multiplier)?.let { written += Component.literal(it) }
			if (spreadSetting.on) {
				priceLine("Bazaar Spread", product.instantBuy - product.instantSell, multiplier)
					?.let { written += Component.literal(it) }
			}
		} else {
			Prices.price(market, PriceSource.LOWEST_BIN)
				?.let { priceLine("Lowest BIN", it, multiplier) }
				?.let { written += Component.literal(it) }
		}
		if (npcSellSetting.on) {
			Prices.price(item.id, PriceSource.NPC_SELL)
				?.let { priceLine("NPC Sell", it, multiplier) }
				?.let { written += Component.literal(it) }
		}
		return written
	}

	private fun fingerprint(multiplier: Int): Int {
		var value = multiplier * FINGERPRINT_STEP + Prices.revision
		value = value * FINGERPRINT_STEP +
			flag(showQualitySetting.on) + flag(showPricesSetting.on) * 2 + flag(npcSellSetting.on) * 4
		return value * FINGERPRINT_STEP + flag(spreadSetting.on) + flag(roundPricesSetting.on) * 2
	}

	private fun flag(on: Boolean): Int = if (on) 1 else 0

	private fun ensurePrices() = priceHold.ensure(showPricesSetting.on)

	private val AGE_DATE: DateTimeFormatter =
		DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.US).withZone(ZoneId.systemDefault())

	private const val AQUA_AFFINITY = "Aqua Affinity"
	private const val DEPTH_STRIDER = "Depth Strider"
	private const val GEAR_SCORE = "Gear Score: "
	private const val GREY_CODE = "§7"
	private const val AGE_INDEX = 1
	private const val MILLIS_PER_SECOND = 1000L
	private const val CATACOMBS = "CATACOMBS"
	private const val NO_SLOT = -1
	private const val STRIP_ROOM = 8
	private const val MASTER_GAP = 19
	private const val MASTER_OFFSET = 3
	private const val POOR = 17
	private const val FAIR = 33
	private const val GOOD = 49
	private const val FULL_STACK = 64
	private const val MAX_LINES = 5
	private const val FINGERPRINT_STEP = 31
	private const val PERCENT = 100f
	private const val MIN_ZOOM = 0.3f
	private const val MAX_ZOOM = 2.0f
	private const val DEFAULT_SCALE = 100.0
	private const val MIN_SCALE = 30.0
	private const val MAX_SCALE = 150.0
	private const val DEFAULT_SPEED = 3.0
	private const val MIN_SPEED = 1.0
	private const val MAX_SPEED = 10.0
	private const val MILLION = 1_000_000.0
	private const val BILLION = 1_000_000_000.0
}
