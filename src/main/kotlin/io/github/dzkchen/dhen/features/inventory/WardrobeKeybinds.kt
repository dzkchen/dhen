package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import java.util.regex.Pattern
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

object WardrobeKeybinds : Module(
	name = "Wardrobe Keybinds",
	category = Category.INVENTORY,
	description = "Equips a wardrobe slot, turns the page or takes off your set with a key while the wardrobe is open."
) {
	private val nextPageSetting =
		MenuKeybinds.menuKey("Next Page", GLFW.GLFW_KEY_RIGHT, "Turns to the next wardrobe page.")
	private val previousPageSetting =
		MenuKeybinds.menuKey("Previous Page", GLFW.GLFW_KEY_LEFT, "Turns to the previous wardrobe page.")
	private val unequipSetting =
		MenuKeybinds.menuKey("Unequip", GLFW.GLFW_KEY_UNKNOWN, "Takes off the armour set you are wearing.")
	private val disableUnequipSetting = BooleanSetting(
		"Disable Unequip",
		description = "Ignores a press that would take off the set you already wear."
	)
	internal val slotSettings = Array(WARDROBE_SLOTS) { index ->
		MenuKeybinds.menuKey(
			"Wardrobe ${index + 1}",
			GLFW.GLFW_KEY_1 + index,
			"Equips wardrobe slot ${index + 1}."
		)
	}

	private val pages = MenuPages("\\((\\d+)/(\\d+)\\) (?:Armor|Equipment) Sets")
	private val equippedName = Pattern.compile("Slot \\d+: Equipped").matcher("")

	init {
		registerSetting(nextPageSetting)
		registerSetting(previousPageSetting)
		registerSetting(unequipSetting)
		registerSetting(disableUnequipSetting)
		for (setting in slotSettings) registerSetting(setting)

		on<ContainerKeyEvent>(priority = AFTER_PRODUCERS) { event ->
			if (pressed(event.screen, event.input.key(), mouse = false)) event.cancelled = true
		}
		on<ContainerClickEvent>(priority = AFTER_PRODUCERS) { event ->
			if (pressed(event.screen, event.click.button(), mouse = true)) event.cancelled = true
		}
	}

	private fun pressed(screen: AbstractContainerScreen<*>, code: Int, mouse: Boolean): Boolean {
		if (!SkyBlockLocation.inSkyBlock) return false
		val nextPage = MenuKeybinds.bound(nextPageSetting, code, mouse)
		val previousPage = MenuKeybinds.bound(previousPageSetting, code, mouse)
		val unequip = MenuKeybinds.bound(unequipSetting, code, mouse)
		val setIndex = MenuKeybinds.boundIndex(code, mouse, slotSettings)
		if (!nextPage && !previousPage && !unequip && setIndex == NO_MENU_BIND) return false
		if (!pages.matches(screen.title)) return false
		if (MenuKeybinds.heldDown(code, mouse, Util.getMillis())) return true
		val slot = when {
			nextPage -> if (pages.current < pages.total) NEXT_PAGE_SLOT else return true
			previousPage -> if (pages.current > 1) PREVIOUS_PAGE_SLOT else return true
			unequip -> equippedSlot(screen).takeIf { it != NO_MENU_SLOT } ?: return true
			else -> {
				val target = FIRST_SET_SLOT + setIndex
				if (disableUnequipSetting.on && equippedSlot(screen) == target) {
					Dhen.announce(ALREADY_EQUIPPED)
					return true
				}
				target
			}
		}
		if (disableUnequipSetting.on && MenuKeybinds.stackAt(screen, slot).isEmpty) return true
		MenuKeybinds.click(screen, slot)
		return true
	}

	private fun equippedSlot(screen: AbstractContainerScreen<*>): Int {
		val slots = screen.menu.slots
		for (index in slots.indices) {
			if (MenuKeybinds.namePrompt(equippedName, slots[index].item)) return slots[index].index
		}
		return NO_MENU_SLOT
	}

	private const val WARDROBE_SLOTS = 9
	private const val FIRST_SET_SLOT = 36
	private const val NEXT_PAGE_SLOT = 53
	private const val PREVIOUS_PAGE_SLOT = 45
	private const val ALREADY_EQUIPPED = "That set is already equipped."
}
