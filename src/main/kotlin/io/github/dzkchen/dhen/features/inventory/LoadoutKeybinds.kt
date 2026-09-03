package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.AFTER_PRODUCERS
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

object LoadoutKeybinds : Module(
	name = "Loadout Keybinds",
	category = Category.INVENTORY,
	description = "Equips a loadout or turns the page with a key while the loadout menu is open."
) {
	private val LOADOUT_SLOTS = intArrayOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)
	private val LOADOUT_KEYS = intArrayOf(
		GLFW.GLFW_KEY_1,
		GLFW.GLFW_KEY_2,
		GLFW.GLFW_KEY_3,
		GLFW.GLFW_KEY_4,
		GLFW.GLFW_KEY_5,
		GLFW.GLFW_KEY_6,
		GLFW.GLFW_KEY_7,
		GLFW.GLFW_KEY_8,
		GLFW.GLFW_KEY_9,
		GLFW.GLFW_KEY_0,
		GLFW.GLFW_KEY_MINUS,
		GLFW.GLFW_KEY_EQUAL
	)

	private val nextPageSetting =
		MenuKeybinds.menuKey("Next Page", GLFW.GLFW_KEY_RIGHT, "Turns to the next loadout page.")
	private val previousPageSetting =
		MenuKeybinds.menuKey("Previous Page", GLFW.GLFW_KEY_LEFT, "Turns to the previous loadout page.")
	private val useHotbarBindsSetting = BooleanSetting(
		"Use Hotbar Binds",
		description = "Uses your vanilla hotbar keys for loadouts 1 to 9 instead of the keys below."
	)
	private val autoCloseSetting = BooleanSetting(
		"Auto Close On Use",
		description = "Closes the loadout menu after equipping, and closes it again if the server reopens it."
	)
	private val blockBarrierSetting = BooleanSetting(
		"Block Barrier Click",
		description = "Ignores clicks on the barrier in the middle of the bottom row."
	)
	private val slotSettings = Array(LOADOUT_SLOTS.size) { index ->
		MenuKeybinds.menuKey("Loadout ${index + 1}", LOADOUT_KEYS[index], "Equips loadout ${index + 1}.")
			.withDependency { !useHotbarBindsSetting.on }
	}

	private val pages = MenuPages("\\((\\d+)/(\\d+)\\) Loadouts?")
	private var closedAt = NOT_CLOSING

	init {
		registerSetting(nextPageSetting)
		registerSetting(previousPageSetting)
		registerSetting(useHotbarBindsSetting)
		registerSetting(autoCloseSetting)
		registerSetting(blockBarrierSetting)
		for (setting in slotSettings) registerSetting(setting)

		on<ContainerKeyEvent>(priority = AFTER_PRODUCERS) { event ->
			val code = event.input.key()
			if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_E) return@on
			if (pressed(event.screen, code, mouse = false)) event.cancelled = true
		}
		on<ContainerClickEvent>(priority = AFTER_PRODUCERS) { event -> clicked(event) }
		on<ContainerReadyEvent>(priority = AFTER_PRODUCERS) { event -> reopened(event) }
	}

	override fun onDisabled() {
		closedAt = NOT_CLOSING
	}

	private fun clicked(event: ContainerClickEvent) {
		val button = event.click.button()
		if (blockBarrierSetting.on &&
			event.hoveredSlot?.index == BARRIER_SLOT &&
			pages.matches(event.screen.title)
		) {
			event.cancelled = true
			return
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) return
		if (pressed(event.screen, button, mouse = true)) event.cancelled = true
	}

	private fun pressed(screen: AbstractContainerScreen<*>, code: Int, mouse: Boolean): Boolean {
		if (!SkyBlockLocation.inSkyBlock) return false
		val nextPage = MenuKeybinds.bound(nextPageSetting, code, mouse)
		val previousPage = MenuKeybinds.bound(previousPageSetting, code, mouse)
		val index = if (useHotbarBindsSetting.on) MenuKeybinds.hotbarIndex(code, mouse, HOTBAR_LOADOUTS)
		else MenuKeybinds.boundIndex(code, mouse, slotSettings)
		if (!nextPage && !previousPage && index == NO_MENU_BIND) return false
		if (!pages.matches(screen.title)) return false
		val now = Util.getMillis()
		if (MenuKeybinds.heldDown(code, mouse, now)) return true
		if (nextPage) {
			if (pages.current < pages.total) MenuKeybinds.click(screen, NEXT_PAGE_SLOT)
			return true
		}
		if (previousPage) {
			if (pages.current > 1) MenuKeybinds.click(screen, PREVIOUS_PAGE_SLOT)
			return true
		}
		val slot = LOADOUT_SLOTS[index]
		if (!MenuKeybinds.lorePrompt(MenuKeybinds.stackAt(screen, slot), EQUIP_PROMPT)) return true
		MenuKeybinds.click(screen, slot)
		if (autoCloseSetting.on) close(now)
		return true
	}

	private fun close(now: Long) {
		val player = Minecraft.getInstance().player ?: return
		player.closeContainer()
		closedAt = now
	}

	private fun reopened(event: ContainerReadyEvent) {
		if (closedAt == NOT_CLOSING) return
		val stale = Util.getMillis() - closedAt > REOPEN_WINDOW_MS
		closedAt = NOT_CLOSING
		if (stale || !pages.matches(event.title)) return
		Minecraft.getInstance().player?.closeContainer()
	}

	private const val NEXT_PAGE_SLOT = 44
	private const val PREVIOUS_PAGE_SLOT = 17
	private const val BARRIER_SLOT = 49
	private const val HOTBAR_LOADOUTS = 9
	private const val NOT_CLOSING = 0L
	private const val REOPEN_WINDOW_MS = 3000L
	private const val EQUIP_PROMPT = "Left-click to equip!"
}
