package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.item.ItemFacts
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object ContainerClicks : Module(
	name = "Container Clicks",
	category = Category.INVENTORY,
	description = "Fixes middle-click in SkyBlock menus and turns ordinary clicks into shift-clicks where that is all you ever want."
) {
	private val middleClickSetting = BooleanSetting(
		"Middle-Click Fix",
		default = true,
		description = "Makes the middle mouse button pick items up in SkyBlock menus again."
	)

	private val huntrapSetting = BooleanSetting(
		"Huntrap Misclick Prevention",
		description = "Refuses clicks on an empty trap in the Hunting Toolkit."
	)

	private val equipmentSetting = BooleanSetting(
		"Shift-Click Equipment",
		description = "Clicking one of your own items in the Stats & Equipment menu equips it straight away."
	)

	private val brewingSetting = BooleanSetting(
		"Shift-Click Brewing",
		description = "Every click in the Brewing Stand moves the item instead of picking it up."
	)

	private val npcSellSetting = BooleanSetting(
		"Shift-Click NPC Sell",
		description = "Clicking one of your own items in an NPC shop sells it straight away."
	)

	private var title = ""
	private var inNpcShop = false
	private var notifiedAt = 0L

	init {
		registerSetting(middleClickSetting)
		registerSetting(huntrapSetting)
		registerSetting(equipmentSetting)
		registerSetting(brewingSetting)
		registerSetting(npcSellSetting)
		on<ContainerReadyEvent> { opened(it.title.string, it.stacks) }
		on<ContainerClosedEvent> { forget() }
		on<ContainerClickEvent> { clicked(it) }
	}

	override fun onDisabled() = forget()

	@JvmStatic
	fun fixesMiddleClick(): Boolean = enabled && middleClickSetting.on && SkyBlockLocation.inSkyBlock

	private fun opened(rawTitle: String, stacks: List<ItemStack>) {
		title = withoutCodes(rawTitle)
		inNpcShop = npcShopOpen(stacks)
	}

	private fun forget() {
		title = ""
		inNpcShop = false
	}

	private fun clicked(event: ContainerClickEvent) {
		if (!SkyBlockLocation.inSkyBlock) return
		val slot = event.hoveredSlot ?: return
		if (huntrapSetting.on && blockedTrap(slot)) {
			event.cancelled = true
			warnOnce()
			return
		}
		if (event.screen !is ContainerScreen) return
		if (!routed(slot)) return
		if (!shiftable(slot, event.click.button())) return
		event.cancelled = true
		clickSlot(event.screen.menu, slot.index, GLFW.GLFW_MOUSE_BUTTON_LEFT, ContainerInput.QUICK_MOVE)
	}

	private fun blockedTrap(slot: Slot): Boolean {
		if (!title.startsWith(HUNTING_TOOLKIT)) return false
		val stack = slot.item
		if (stack.isEmpty) return false
		return ItemFacts.loreContains(stack, EMPTY_TRAP)
	}

	private fun routed(slot: Slot): Boolean {
		if (brewingSetting.on && title.startsWith(BREWING_STAND) && slot.index != BREWING_CLOSE_SLOT) return true
		if (slot.container !is Inventory || slot.item.isEmpty) return false
		if (equipmentSetting.on && title == EQUIPMENT_MENU) return true
		return npcSellSetting.on && inNpcShop
	}

	private fun shiftable(slot: Slot, button: Int): Boolean =
		button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !ItemFacts.isSack(slot.item)

	private fun warnOnce() {
		val now = System.currentTimeMillis()
		if (now - notifiedAt < NOTICE_GAP_MS) return
		notifiedAt = now
		Notifications.push(TRAP_ICON, name, "Stopped a click on an empty trap.")
	}

	private const val BREWING_CLOSE_SLOT = 49
	private const val NOTICE_GAP_MS = 10_000L
	private const val HUNTING_TOOLKIT = "Hunting Toolkit"
	private const val BREWING_STAND = "Brewing Stand"
	private const val EQUIPMENT_MENU = "Stats & Equipment"
	private const val EMPTY_TRAP = "Status: EMPTY"
	private const val TRAP_ICON = "⛔"
}
