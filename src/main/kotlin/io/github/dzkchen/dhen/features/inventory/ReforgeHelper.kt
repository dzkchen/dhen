package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.RequirementHold
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFacts
import io.github.dzkchen.dhen.data.item.ItemRarity
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.Reforge
import io.github.dzkchen.dhen.data.repo.RepoConstants
import io.github.dzkchen.dhen.data.repo.reforgeTypes
import io.github.dzkchen.dhen.event.BEFORE_FEATURES
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerScrollEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.event.SlotRenderEvent
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.SlotTint
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.gui.slotText
import io.github.dzkchen.dhen.input.platformModifierHeld
import io.github.dzkchen.dhen.input.platformModifierName
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.ui.hud.HUD_MARGIN
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.MenuHudEditor
import io.github.dzkchen.dhen.util.matcher
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale
import org.lwjgl.glfw.GLFW

internal const val REFORGE_SORT = 0
internal const val REFORGE_ROW = 1

object ReforgeHelper : Module(
	name = "Reforge Helper",
	category = Category.INVENTORY,
	description = "Lists every reforge the item in the reforge menu can take and what each one does to its stats."
) {
	private val stonesHexOnlySetting = BooleanSetting(
		"Stones Hex Only",
		default = true,
		description = "Lists reforge stones only while you are in the Hex, so the blacksmith lists only what it can give."
	)

	private val blockRareSetting = BooleanSetting(
		"Block Rare Reforge",
		default = true,
		description = "Refuses a reforge that would wipe a reforge the blacksmith cannot put back."
	)

	private val showDiffSetting = BooleanSetting(
		"Show Diff",
		description = "Adds how far each stat moves from the reforge the item already has."
	)

	private val hideChatSetting = BooleanSetting(
		"Hide Chat",
		description = "Hides Hypixel's own reforge messages."
	)

	private val repoHold = RequirementHold(ItemRepo::active, ItemRepo::require)

	private val eligible = ArrayList<Reforge>()
	private val statNames = ArrayList<String>()
	private val detailLines = ArrayList<String>()
	private val statKeys = LinkedHashSet<String>()
	private val ability = DhenType.wrap()
	private val tints = IntArray(MENU_CELLS)
	private val nameMemo: TextMemo = DhenType.memo()
	private val warningMemo: TextMemo = DhenType.memo()

	private val success = matcher("You reforged your .+ into an? .+!|You applied an? .+ to your .+!")
	private val rateLimited = matcher("Wait a moment before reforging again!|Whoa! Slow down there!")

	internal val element = hud(ReforgeHelperElement())

	private var inMenu = false
	private var inHex = false
	private var rareBlocked = false
	private var warningText = ""
	private var itemId = ""
	private var itemCategory = ""
	private var rarity = ItemRarity.NONE
	private var currentModifier = ""
	private var current: Reforge? = null
	private var target: Reforge? = null
	private var hovered: Reforge? = null
	private var waitingForChat = false
	private var waitDelay = false
	private var sortStat = ""
	private var builtCommit: String? = null

	init {
		registerSetting(stonesHexOnlySetting)
		registerSetting(blockRareSetting)
		registerSetting(showDiffSetting)
		registerSetting(hideChatSetting)

		on<ClientTickEvent.End> { ticked() }
		on<ContainerReadyEvent> { opened(it.title.string, it.stacks) }
		on<ContainerUpdatedEvent> { opened(it.title.string, it.stacks) }
		on<ContainerClosedEvent> { forget() }
		on<WorldChangeEvent> { forget() }
		on<ChatReceiveEvent> { chatted(it) }
		on<ContainerClickEvent> { clicked(it) }
		on<ContainerScrollEvent>(BEFORE_FEATURES) { scrolled(it) }
		on<ScreenRenderEvent.Post> { pointed(it) }
		on<SlotRenderEvent.Pre> { tinted(it) }
		on<SlotRenderEvent.Post> { marked(it) }
	}

	override fun onEnabled() = repoHold.ensure()

	override fun onDisabled() {
		repoHold.release()
		forget()
	}

	internal fun rowCount(): Int = eligible.size

	internal fun rowText(index: Int): String = eligible[index].reforge

	internal fun rowInk(index: Int): Int {
		val reforge = eligible[index]
		return when {
			reforge.modifier == currentModifier -> DhenPalette.SLOT_GOLD
			reforge.modifier == target?.modifier -> DhenPalette.accent
			reforge.stone.isNotEmpty() -> DhenPalette.SLOT_BLUE
			else -> DhenPalette.TEXT_SECONDARY
		}
	}

	internal fun sortLabel(): String = if (sortStat.isEmpty()) DEFAULT_SORT else statLabel(sortStat)

	internal fun detail(): List<String> = detailLines

	private fun ticked() {
		repoHold.ensure()
		if (!inMenu || builtCommit == ItemRepo.commit) return
		current = ItemRepo.constants.reforge(currentModifier)
		rebuild()
	}

	private fun itemSlot(): Int = if (inHex) HEX_ITEM_SLOT else BLACKSMITH_ITEM_SLOT

	private fun buttonSlot(): Int = if (inHex) HEX_BUTTON_SLOT else BLACKSMITH_BUTTON_SLOT

	private fun opened(rawTitle: String, stacks: List<ItemStack>) {
		val title = withoutCodes(rawTitle)
		val hex = title == HEX_MENU
		if (!SkyBlockLocation.inSkyBlock || (!hex && title != BLACKSMITH_MENU)) {
			forget()
			return
		}
		val entering = !inMenu
		if (entering) {
			inMenu = true
			waitingForChat = false
			waitDelay = false
		}
		inHex = hex
		read(stacks.getOrNull(itemSlot()) ?: ItemStack.EMPTY, entering)
		paint()
	}

	private fun forget() {
		if (!inMenu) return
		inMenu = false
		inHex = false
		rareBlocked = false
		itemId = ""
		itemCategory = ""
		rarity = ItemRarity.NONE
		currentModifier = ""
		current = null
		target = null
		hovered = null
		waitingForChat = false
		waitDelay = false
		sortStat = ""
		builtCommit = null
		eligible.clear()
		statNames.clear()
		detailLines.clear()
		tints.fill(0)
		element.clear()
	}

	private fun reread() {
		if (!inMenu) return
		val menu = Minecraft.getInstance().player?.containerMenu ?: return
		read(menu.slots.getOrNull(itemSlot())?.item ?: ItemStack.EMPTY, false)
	}

	private fun read(stack: ItemStack, forced: Boolean) {
		val item = SkyBlockItems.of(stack)
		val id = item.id
		if (id != itemId) target = null
		val held = if (stack.isEmpty) ItemRarity.NONE else SkyBlockItems.rarity(stack)
		if (!forced && id == itemId && item.reforge == currentModifier && held == rarity) return
		itemId = id
		itemCategory = if (stack.isEmpty) "" else ItemFacts.category(stack)
		rarity = held
		currentModifier = item.reforge
		current = ItemRepo.constants.reforge(currentModifier)
		rebuild()
	}

	private fun rebuild() {
		builtCommit = ItemRepo.commit
		eligible.clear()
		statNames.clear()
		if (SkyBlockLocation.inSkyBlock && itemId.isNotEmpty() && rarity != ItemRarity.NONE) {
			val constants = ItemRepo.constants
			val types = reforgeTypes(itemCategory)
			collect(constants.blacksmithReforges, types)
			if (inHex || !stonesHexOnlySetting.on) collect(constants.stoneReforges, types)
			for (reforge in eligible) {
				val stats = reforge.stats[rarity.name] ?: continue
				for (stat in stats.keys) if (stat !in statNames) statNames += stat
			}
			if (sortStat.isNotEmpty() && sortStat !in statNames) sortStat = ""
			sort()
		}
		refresh()
	}

	private fun collect(pool: Collection<Reforge>, types: List<String>) {
		for (reforge in pool) if (fits(reforge, types)) eligible += reforge
	}

	private fun fits(reforge: Reforge, types: List<String>): Boolean {
		for (type in reforge.itemTypes) if (type == itemId || type in types) return true
		return false
	}

	private fun sort() {
		val stat = sortStat
		if (stat.isEmpty()) {
			eligible.sortBy { it.stone.isNotEmpty() }
		} else {
			eligible.sortByDescending { it.stats[rarity.name]?.get(stat) ?: 0.0 }
		}
	}

	private fun refresh() {
		rebuildDetail()
		element.rebuild()
		paint()
	}

	private fun rebuildDetail() {
		detailLines.clear()
		val shown = hovered ?: current ?: return
		detailLines += ""
		detailLines += shown.reforge
		statLines(shown)
		abilityLines(shown)
	}

	private fun statLines(reforge: Reforge) {
		val stats = reforge.stats[rarity.name].orEmpty()
		val held = if (showDiffSetting.on && reforge.modifier != currentModifier) {
			current?.stats?.get(rarity.name)
		} else {
			null
		}
		if (held == null) {
			for ((stat, value) in stats) detailLines += "${statLabel(stat)}  ${signed(value)}"
			return
		}
		statKeys.clear()
		statKeys += stats.keys
		statKeys += held.keys
		for (stat in statKeys) {
			val value = stats[stat] ?: 0.0
			val delta = value - (held[stat] ?: 0.0)
			detailLines += "${statLabel(stat)}  ${signed(value)}  (${signed(delta)})"
		}
	}

	private fun abilityLines(reforge: Reforge) {
		val text = reforge.ability[rarity.name] ?: reforge.ability[RepoConstants.ANY_RARITY] ?: return
		ability.measure(Minecraft.getInstance().font, withoutCodes(text), ABILITY_WIDTH, ABILITY_LINES)
		for (index in 0 until ability.lines) detailLines += ability.line(index)
	}

	private fun statLabel(stat: String): String =
		stat.split('_').joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar) }

	private fun signed(value: Double): String {
		val whole = value.toLong()
		val text = if (whole.toDouble() == value) whole.toString() else value.toString()
		return if (value < 0.0) text else "+$text"
	}

	private fun pointed(event: ScreenRenderEvent.Post) {
		element.pointer(event.mouseX, event.mouseY)
		if (!inMenu) return
		val action = element.hoveredAction()
		val under = if (action >= REFORGE_ROW) eligible.getOrNull(action - REFORGE_ROW) else null
		if (under?.modifier == hovered?.modifier) return
		hovered = under
		refresh()
	}

	private fun clicked(event: ContainerClickEvent) {
		if (!inMenu || !SkyBlockLocation.inSkyBlock) return
		rareBlocked = false
		if (!MenuHudEditor.editing && event.click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && overlayClick(event)) return
		val slot = event.hoveredSlot ?: return
		if (slot.index != buttonSlot() || slot.container is Inventory) return
		val lore = SkyBlockItems.rawLore(slot.item)
		if (withoutCodes(lore.lastOrNull()?.string.orEmpty()) != CLICK_TO_REFORGE) return
		if (blockedRareReforge(event)) return
		guarded(event)
	}

	private fun overlayClick(event: ContainerClickEvent): Boolean {
		val action = element.hoveredAction()
		if (action == REFORGE_SORT) {
			cycleSort(CYCLE_FORWARD)
		} else if (action >= REFORGE_ROW) {
			target = eligible.getOrNull(action - REFORGE_ROW) ?: return false
			play(SoundEvents.UI_BUTTON_CLICK.value())
			refresh()
		} else {
			return false
		}
		event.cancelled = true
		return true
	}

	private fun scrolled(event: ContainerScrollEvent) {
		if (MenuHudEditor.editing || !inMenu || event.scrollY == 0.0) return
		if (element.hoveredAction() != REFORGE_SORT) return
		cycleSort(if (event.scrollY > 0.0) CYCLE_FORWARD else CYCLE_BACKWARD)
		event.cancelled = true
	}

	private fun cycleSort(step: Int) {
		if (statNames.isEmpty()) return
		val wheel = statNames.size + 1
		val next = ((statNames.indexOf(sortStat) + 1 + step) % wheel + wheel) % wheel
		sortStat = if (next == 0) "" else statNames[next - 1]
		sort()
		refresh()
	}

	private fun blockedRareReforge(event: ContainerClickEvent): Boolean {
		if (!blockRareSetting.on) return false
		val held = current ?: return false
		if (held.stone.isEmpty() || platformModifierHeld()) return false
		rareBlocked = true
		warningText = "This item has a non-Blacksmith reforge! (${platformModifierName()} to bypass)"
		play(SoundEvents.EXPERIENCE_ORB_PICKUP)
		event.cancelled = true
		return true
	}

	private fun guarded(event: ContainerClickEvent) {
		val wanted = target ?: return
		if (wanted.modifier == currentModifier) {
			event.cancelled = true
			waitingForChat = false
			play(SoundEvents.EXPERIENCE_ORB_PICKUP)
			return
		}
		if (waitingForChat) {
			waitDelay = true
			event.cancelled = true
			return
		}
		if (event.click.button() == MIDDLE_BUTTON) return
		if (waitDelay) waitDelay = false else waitingForChat = true
	}

	private fun chatted(event: ChatReceiveEvent) {
		if (!inMenu || !SkyBlockLocation.inSkyBlock) return
		val message = event.stripped
		val reforged = success.get().reset(message).matches()
		if (!reforged && !rateLimited.get().reset(message).matches()) return
		inTicks(REFORGE_SETTLE_TICKS) {
			if (reforged) reread()
			waitingForChat = false
		}
		if (hideChatSetting.on) event.cancelled = true
	}

	private fun paint() {
		tints.fill(0)
		if (!inMenu) return
		val under = hovered
		if (under != null && inHex) {
			if (under.modifier == currentModifier) {
				tints[itemSlot()] = hoverTint()
			} else {
				paintStone(under, hoverTint())
			}
		}
		val wanted = target ?: return
		when {
			wanted.modifier == currentModifier -> tints[itemSlot()] = finishedTint()
			wanted.stone.isEmpty() -> tints[buttonSlot()] = selectedTint()
			inHex -> paintStone(wanted, selectedTint())
			else -> tints[EXIT_BUTTON] = selectedTint()
		}
	}

	private fun paintStone(reforge: Reforge, tint: Int) {
		val name = stoneName(reforge)
		val slots = Minecraft.getInstance().player?.containerMenu?.slots
		if (slots != null) {
			for (slot in slots) {
				if (slot.container is Inventory || slot.index < 0 || slot.index >= MENU_CELLS) continue
				if (slot.item.isEmpty || ItemFacts.cleanName(slot.item) != name) continue
				tints[slot.index] = tint
				return
			}
			paintPage(slots, HEX_PAGE_UP, tint)
			paintPage(slots, HEX_PAGE_DOWN, tint)
		}
	}

	private fun paintPage(slots: List<Slot>, index: Int, tint: Int) {
		val slot = slots.getOrNull(index) ?: return
		if (slot.item.`is`(Items.PLAYER_HEAD)) tints[index] = tint
	}

	private fun stoneName(reforge: Reforge): String {
		if (reforge.stone.isEmpty()) return RANDOM_BASIC_REFORGE
		return withoutCodes(ItemRepo.item(reforge.stone)?.displayName.orEmpty())
	}

	private fun tinted(event: SlotRenderEvent.Pre) {
		if (!inMenu || !SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		if (slot.container is Inventory || slot.index < 0 || slot.index >= MENU_CELLS) return
		val tint = tints[slot.index]
		if (tint != 0) SlotTint.claim(tint, TINT_PRIORITY)
	}

	private fun marked(event: SlotRenderEvent.Post) {
		if (!inMenu || !SkyBlockLocation.inSkyBlock) return
		val slot = event.slot
		if (slot.container is Inventory) return
		if (slot.index == itemSlot()) {
			val name = current?.reforge ?: return
			slotText(event.graphics, name, slot.x - NAME_GAP, slot.y, TEXT_SCALE, DhenPalette.SLOT_YELLOW, nameMemo)
			return
		}
		if (!rareBlocked || slot.index != buttonSlot()) return
		slotText(
			event.graphics,
			warningText,
			slot.x - WARNING_GAP,
			slot.y + WARNING_DROP,
			TEXT_SCALE,
			DhenPalette.SLOT_RED,
			warningMemo
		)
	}

	private fun hoverTint(): Int = DhenPalette.withAlpha(DhenPalette.SLOT_GOLD, HOVER_ALPHA)

	private fun selectedTint(): Int = DhenPalette.withAlpha(DhenPalette.SLOT_BLUE, SELECTED_ALPHA)

	private fun finishedTint(): Int = DhenPalette.withAlpha(DhenPalette.SLOT_GREEN, FINISHED_ALPHA)

	private fun play(sound: SoundEvent) {
		Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, 1.0f, 1.0f))
	}

	internal const val DEFAULT_SORT = "Default"

	private const val HEX_MENU = "The Hex ➜ Reforges"
	private const val BLACKSMITH_MENU = "Reforge Item"
	private const val CLICK_TO_REFORGE = "Click to reforge!"
	private const val RANDOM_BASIC_REFORGE = "Random Basic Reforge"
	private const val HEX_ITEM_SLOT = 19
	private const val BLACKSMITH_ITEM_SLOT = 13
	private const val HEX_BUTTON_SLOT = 48
	private const val BLACKSMITH_BUTTON_SLOT = 22
	private const val HEX_PAGE_UP = 17
	private const val HEX_PAGE_DOWN = 35
	private const val EXIT_BUTTON = 40
	private const val MIDDLE_BUTTON = 2
	private const val REFORGE_SETTLE_TICKS = 2
	private const val ABILITY_WIDTH = 170
	private const val ABILITY_LINES = 3
	private const val NAME_GAP = 5
	private const val WARNING_GAP = 55
	private const val WARNING_DROP = 20
	private const val TEXT_SCALE = 1f
	private const val TINT_PRIORITY = 25
	private const val HOVER_ALPHA = 50
	private const val SELECTED_ALPHA = 100
	private const val FINISHED_ALPHA = 75
}

internal class ReforgeHelperElement : MenuListElement("Reforge Helper", HudAnchor.TOP_LEFT, HUD_MARGIN, HUD_MARGIN) {
	fun rebuild() {
		clearLines(retainHover = true)
		line().text = HEADER
		val rows = ReforgeHelper.rowCount()
		if (rows > 0) {
			val sort = line()
			sort.text = SORT_LABEL + ReforgeHelper.sortLabel()
			sort.action = REFORGE_SORT
		}
		for (index in 0 until rows) {
			val row = line()
			row.text = ReforgeHelper.rowText(index)
			row.ink = ReforgeHelper.rowInk(index)
			row.action = REFORGE_ROW + index
		}
		for (text in ReforgeHelper.detail()) line().text = text
	}

	private companion object {
		const val HEADER = "Reforges"
		const val SORT_LABEL = "Sorted by: "
	}
}
