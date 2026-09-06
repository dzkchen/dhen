package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFacts
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.LAST_WORD
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.input.keyDisplayName
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack

object FocusMode : Module(
	name = "Focus Mode",
	category = Category.INVENTORY,
	description = "Cuts item tooltips down to the item's name, keeping the auction details while you browse auctions."
) {
	private val toggleKeySetting = MenuKeybinds.menuKey(
		"Toggle Key",
		NO_MENU_BIND,
		"Press this in a menu to turn the short tooltips on and off."
	).onPress { active = !active }

	private val disableHintSetting = BooleanSetting(
		"Disable Hint",
		description = "Removes the tooltip line that names the key turning this on and off."
	)

	private val alwaysEnabledSetting = BooleanSetting(
		"Always Enabled",
		description = "Keeps the short tooltips on all the time and ignores the Toggle Key."
	)

	private val keepMenuItemsSetting = BooleanSetting(
		"Keep Menu Items",
		default = true,
		description = "Leaves menu buttons and other non-SkyBlock items showing their whole description."
	)

	private var active = false
	private var inAuctions = false
	private var inBazaar = false
	private var hintCode = NO_MENU_BIND
	private val tail = ArrayList<Component>(AUCTION_LINES)
	private var tailStack: ItemStack? = null
	private var tailCount = 0
	private var tailStart = NO_SEPARATOR
	private var enableHint: Component? = null
	private var activeHint: Component? = null
	private var disableHint: Component? = null

	init {
		registerSetting(toggleKeySetting)
		registerSetting(disableHintSetting)
		registerSetting(alwaysEnabledSetting)
		registerSetting(keepMenuItemsSetting)
		on<ContainerReadyEvent> { opened(it.title.string, it.stacks) }
		on<ContainerClosedEvent> { forget() }
		on<TooltipEvent>(LAST_WORD) { condense(it) }
	}

	override fun onDisabled() {
		active = false
		forget()
	}

	internal fun condense(event: TooltipEvent) {
		if (!SkyBlockLocation.inSkyBlock || event.lines.isEmpty()) return
		if (keepMenuItemsSetting.on && keptWhole(event)) return
		val hinted = rememberHints()
		if (!active && !alwaysEnabledSetting.on) {
			if (hinted) event.edit().add(1, enableHint!!)
			return
		}
		val kept = auctionTail(event.stack, event.lines)
		val lines = event.edit()
		val name = lines[0]
		lines.clear()
		lines.add(name)
		if (hinted) {
			lines.add(activeHint!!)
			lines.add(disableHint!!)
		}
		lines.addAll(kept)
	}

	private fun rememberHints(): Boolean {
		if (disableHintSetting.on || alwaysEnabledSetting.on || !toggleKeySetting.isBound) return false
		val code = toggleKeySetting.code
		if (code == hintCode && enableHint != null) return true
		hintCode = code
		val key = keyDisplayName(code)
		enableHint = Component.literal("$HINT$PRESS$key$TO_SHORTEN")
		activeHint = Component.literal("${HINT}Focus Mode is on.")
		disableHint = Component.literal("$HINT$PRESS$key$TO_RESTORE")
		return true
	}

	internal fun opened(rawTitle: String, stacks: List<ItemStack>) {
		val title = withoutCodes(rawTitle)
		inAuctions = title.startsWith(AUCTIONS)
		inBazaar = bazaarMenuOpen(title, stacks)
	}

	private fun forget() {
		inAuctions = false
		inBazaar = false
		tailStack = null
	}

	private fun keptWhole(event: TooltipEvent): Boolean {
		val id = SkyBlockItems.of(event.stack).id
		if (id.isEmpty() || id == ItemFacts.SKYBLOCK_MENU) return true
		return inBazaar && event.hoveredSlot.container is SimpleContainer
	}

	private fun auctionTail(stack: ItemStack, lines: List<Component>): List<Component> {
		tail.clear()
		if (!inAuctions) return tail
		if (stack !== tailStack || lines.size != tailCount) {
			tailStack = stack
			tailCount = lines.size
			tailStart = separatorAt(lines)
		}
		if (tailStart < 0) return tail
		val end = minOf(lines.size, tailStart + AUCTION_LINES)
		for (index in tailStart until end) tail += lines[index]
		return tail
	}

	private fun separatorAt(lines: List<Component>): Int {
		for (index in lines.indices) if (lines[index].string.contains(SEPARATOR)) return index
		return NO_SEPARATOR
	}

	private const val AUCTIONS = "Auctions"
	private const val AUCTION_LINES = 20
	private const val NO_SEPARATOR = -1
	private const val SEPARATOR = "-----------------"
	private const val HINT = "§7"
	private const val PRESS = "Press "
	private const val TO_SHORTEN = " to shorten this tooltip."
	private const val TO_RESTORE = " to show the whole tooltip again."
}
