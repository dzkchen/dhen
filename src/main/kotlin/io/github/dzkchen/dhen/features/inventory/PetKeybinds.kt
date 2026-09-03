package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.features.visual.PetDisplay
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

object PetKeybinds : Module(
	name = "Pet Keybinds",
	category = Category.INVENTORY,
	description = "Summons a pet, turns the page or despawns your pet with a key while the pet menu is open."
) {
	private val PET_SLOTS = intArrayOf(10, 11, 12, 13, 14, 15, 16, 19, 20)

	private val unequipSetting =
		MenuKeybinds.menuKey("Unequip", GLFW.GLFW_KEY_UNKNOWN, "Despawns the pet you have out.")
	private val nextPageSetting =
		MenuKeybinds.menuKey("Next Page", GLFW.GLFW_KEY_UNKNOWN, "Turns to the next pet page.")
	private val previousPageSetting =
		MenuKeybinds.menuKey("Previous Page", GLFW.GLFW_KEY_UNKNOWN, "Turns to the previous pet page.")
	private val disableUnequipSetting = BooleanSetting(
		"Disable Unequip",
		description = "Ignores a press that would despawn the pet you already have out."
	)
	private val closeIfEquippedSetting = BooleanSetting(
		"Close If Already Equipped",
		description = "Closes the pet menu instead when you press the key of the pet you already have out."
	)
	private val slotSettings = Array(PET_SLOTS.size) { index ->
		MenuKeybinds.menuKey("Pet ${index + 1}", GLFW.GLFW_KEY_1 + index, "Summons pet ${index + 1}.")
	}

	private val pages = MenuPages("^(?:\\((\\d+)/(\\d+)\\) )?Pets(?:: \".*\")?(?: \\((\\d+)/(\\d+)\\))? ?$", pageless = true)

	init {
		registerSetting(unequipSetting)
		registerSetting(nextPageSetting)
		registerSetting(previousPageSetting)
		registerSetting(disableUnequipSetting)
		registerSetting(closeIfEquippedSetting)
		for (setting in slotSettings) registerSetting(setting)

		on<ContainerKeyEvent>(priority = AFTER_PRODUCERS) { event ->
			if (pressed(event.screen, event.input.key(), mouse = false)) event.cancelled = true
		}
		on<ContainerClickEvent>(priority = AFTER_PRODUCERS) { event ->
			if (pressed(event.screen, event.click.button(), mouse = true)) event.cancelled = true
		}
	}

	private fun pressed(screen: AbstractContainerScreen<*>, code: Int, mouse: Boolean): Boolean {
		if (!SkyBlockLocation.inSkyBlock || PetDisplay.wheelOwns(screen)) return false
		val nextPage = MenuKeybinds.bound(nextPageSetting, code, mouse)
		val previousPage = MenuKeybinds.bound(previousPageSetting, code, mouse)
		val unequipping = MenuKeybinds.bound(unequipSetting, code, mouse)
		val petIndex = MenuKeybinds.boundIndex(code, mouse, slotSettings)
		if (!nextPage && !previousPage && !unequipping && petIndex == NO_MENU_BIND) return false
		if (!pages.matches(screen.title)) return false
		if (MenuKeybinds.heldDown(code, mouse, Util.getMillis())) return true
		var slot = when {
			nextPage -> if (pages.current < pages.total) NEXT_PAGE_SLOT else return announce(LAST_PAGE)
			previousPage -> if (pages.current > 1) PREVIOUS_PAGE_SLOT else return announce(FIRST_PAGE)
			unequipping -> summonedSlot(screen).takeIf { it != NO_MENU_SLOT } ?: return announce(NO_SUMMONED_PET)
			else -> PET_SLOTS[petIndex]
		}
		if (!unequipping && MenuKeybinds.lorePrompt(MenuKeybinds.stackAt(screen, slot), DESPAWN_PROMPT)) {
			Dhen.announce(ALREADY_SUMMONED)
			if (closeIfEquippedSetting.on) slot = CLOSE_SLOT
			else if (disableUnequipSetting.on) return true
		}
		MenuKeybinds.click(screen, slot)
		return true
	}

	private fun summonedSlot(screen: AbstractContainerScreen<*>): Int {
		val slots = screen.menu.slots
		val last = minOf(SCAN_LAST, slots.size - 1)
		for (index in SCAN_FIRST..last) {
			if (MenuKeybinds.lorePrompt(slots[index].item, DESPAWN_PROMPT)) return slots[index].index
		}
		return NO_MENU_SLOT
	}

	private fun announce(line: String): Boolean {
		Dhen.announce(line)
		return true
	}

	private const val SCAN_FIRST = 10
	private const val SCAN_LAST = 42
	private const val NEXT_PAGE_SLOT = 53
	private const val PREVIOUS_PAGE_SLOT = 45
	private const val CLOSE_SLOT = 49
	private const val DESPAWN_PROMPT = "Click to despawn!"
	private const val LAST_PAGE = "You are already on the last page."
	private const val FIRST_PAGE = "You are already on the first page."
	private const val NO_SUMMONED_PET = "Could not find the pet you have out."
	private const val ALREADY_SUMMONED = "That pet is already out."
}
