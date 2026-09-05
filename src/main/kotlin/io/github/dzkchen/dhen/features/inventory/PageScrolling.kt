package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerScrollEvent
import io.github.dzkchen.dhen.event.legacyCodes
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.input.keyHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW
import java.util.regex.Pattern

object PageScrolling : Module(
	name = "Page Scrolling",
	category = Category.INVENTORY,
	description = "Turns the pages of a multi-page SkyBlock menu with the mouse wheel."
) {
	private val bypassKeySetting = MenuKeybinds.menuKey(
		"Bypass Key",
		GLFW.GLFW_KEY_LEFT_SHIFT,
		"Hold this to scroll pages while the cursor sits over a slot."
	)

	private val invertBypassSetting = BooleanSetting(
		"Invert Bypass",
		description = "Flips the Bypass Key so holding it blocks scrolling over a slot instead of allowing it."
	)

	private val invertScrollSetting = BooleanSetting(
		"Invert Scroll",
		description = "Turns the wheel the other way round."
	)

	private var title = ""
	private var scrollable = false
	private var readyAt = 0L

	init {
		registerSetting(bypassKeySetting)
		registerSetting(invertBypassSetting)
		registerSetting(invertScrollSetting)
		on<ContainerReadyEvent> { opened(it.title.string) }
		on<ContainerClosedEvent> { forget() }
		on<ContainerScrollEvent> { scrolled(it) }
	}

	override fun onDisabled() = forget()

	private fun opened(rawTitle: String) {
		title = withoutCodes(rawTitle)
		scrollable = true
	}

	private fun forget() {
		title = ""
		scrollable = false
	}

	private fun scrolled(event: ContainerScrollEvent) {
		if (!SkyBlockLocation.inSkyBlock || event.screen !is ContainerScreen) return
		if (!scrollableMenu(title)) return
		if (!scrollable && System.currentTimeMillis() < readyAt) return
		if (event.hoveredSlot != null && invertBypassSetting.on == keyHeld(bypassKeySetting.code)) return
		if (event.scrollY == 0.0) return
		val wanted = if ((event.scrollY > 0.0) != invertScrollSetting.on) FORWARD_STEP else BACKWARD_STEP
		val button = pageButton(event.screen.menu, wanted)
		if (button == NO_MENU_SLOT) return
		event.cancelled = true
		clickSlot(event.screen.menu, button, LEFT_BUTTON, ContainerInput.PICKUP)
		scrollable = false
		readyAt = System.currentTimeMillis() + COOLDOWN_MS
	}

	private fun pageButton(menu: AbstractContainerMenu, wanted: Int): Int {
		val slots = menu.slots
		for (index in slots.indices) {
			val slot = slots[index]
			if (slot.container is Inventory) break
			val stack = slot.item
			if (stack.isEmpty) continue
			if (pageStep(legacyCodes(stack.hoverName)) == wanted) return index
		}
		return NO_MENU_SLOT
	}

	private const val LEFT_BUTTON = 0
	private const val COOLDOWN_MS = 1_000L
}

internal const val FORWARD_STEP = 1

internal const val BACKWARD_STEP = -1

internal const val NO_STEP = 0

internal fun scrollableMenu(title: String): Boolean =
	title.isNotEmpty() && !PLAIN_CHEST.get().reset(title).matches()

internal fun pageStep(name: String): Int = when {
	FORWARD.get().reset(name).matches() -> FORWARD_STEP
	BACKWARD.get().reset(name).matches() -> BACKWARD_STEP
	else -> NO_STEP
}

private val PLAIN_CHEST = pageMatcher("Large Chest|Chest")

private val FORWARD = pageMatcher("§aNext Page|§aScroll Up|§aLevels 26 - 50|§aNext Page →|§aScroll Right")

private val BACKWARD = pageMatcher("§aPrevious Page|§aScroll Down|§aLevels 1 - 25|§a← Previous Page|§aScroll Left")

private fun pageMatcher(pattern: String) = ThreadLocal.withInitial { Pattern.compile(pattern).matcher("") }
