package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ContainerClosedEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.ContainerUpdatedEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.input.shiftHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

object StorageOverlay : Module(
	name = "Storage Overlay",
	category = Category.INVENTORY,
	description = "Shows every ender chest and backpack page on one screen, and previews a storage you have opened before."
) {
	internal val showOverlaySetting = BooleanSetting(
		"Show Overlay",
		default = true,
		description = "Replaces the storage screen with every page at once."
	)

	internal val previewSetting = BooleanSetting(
		"Backpack Preview",
		default = true,
		description = "Shift-hover a storage in the Storage menu to see what is inside it."
	)

	internal val scaleSetting = NumberSetting(
		"Scale",
		default = 1.0,
		min = 0.5,
		max = 2.0,
		step = 0.05,
		description = "The size of the overlay."
	)

	internal val columnsSetting = NumberSetting(
		"Columns",
		default = 3.0,
		min = 1.0,
		max = 10.0,
		description = "How many pages sit side by side."
	)

	internal val maxHeightSetting = NumberSetting(
		"Max Height",
		default = 324.0,
		min = 80.0,
		max = 600.0,
		description = "The tallest the overlay may grow."
	)

	internal val scrollSpeedSetting = NumberSetting(
		"Scroll Speed",
		default = 10.0,
		min = 1.0,
		max = 50.0,
		description = "How far one notch of the wheel moves the pages."
	)

	internal val retainScrollSetting = BooleanSetting(
		"Retain Scroll",
		default = true,
		description = "Keeps the scroll position after the overlay closes."
	)

	internal val tooltipScrollSetting = BooleanSetting(
		"Tooltip Scroll",
		default = false,
		description = "Sends the wheel to the hovered item's tooltip instead of the pages."
	)

	internal val hideNonMatchingSetting = BooleanSetting(
		"Hide Non-Matching Pages",
		default = false,
		description = "While the inventory search is open, hides pages holding nothing that matches."
	)

	const val NOT_STORAGE = -2
	const val OVERVIEW = -1

	private const val TRANSITION_NANOS = 1_000_000_000L

	private val enderTitle = Pattern.compile("^Ender Chest.*\\((\\d+)/\\d+\\)$", Pattern.CASE_INSENSITIVE).matcher("")
	private val backpackTitle = Pattern.compile("^.*Backpack.*\\(Slot #(\\d+)\\)$", Pattern.CASE_INSENSITIVE).matcher("")

	private var navigatingUntil = Long.MIN_VALUE
	private var titleSeen: Component? = null
	private var titlePage = NOT_STORAGE

	init {
		for (setting in listOf(
			showOverlaySetting,
			previewSetting,
			scaleSetting,
			columnsSetting,
			maxHeightSetting,
			scrollSpeedSetting,
			retainScrollSetting,
			tooltipScrollSetting,
			hideNonMatchingSetting
		)) registerSetting(setting)

		on<ContainerReadyEvent> { remember(it.title, it.stacks, save = true) }
		on<ContainerUpdatedEvent> { remember(it.title, it.stacks, save = false) }
		on<ContainerClosedEvent> { StorageSnapshots.flush() }
		on<GuiOpenEvent> { swap(it) }
		on<GuiCloseEvent> { closed(it) }
		on<TooltipEvent> { preview(it) }
	}

	override fun onDisabled() {
		navigatingUntil = Long.MIN_VALUE
		StorageSnapshots.flush()
		StorageOverlayScreen.forgetScroll()
	}

	internal fun storagePage(title: Component): Int {
		if (title === titleSeen) return titlePage
		titleSeen = title
		titlePage = resolve(title)
		return titlePage
	}

	private fun resolve(title: Component): Int {
		val text = withoutCodes(title.string).trim()
		if (text.equals(OVERVIEW_TITLE, ignoreCase = true)) return OVERVIEW
		if (enderTitle.reset(text).matches()) {
			val page = enderTitle.group(1).toIntOrNull() ?: return NOT_STORAGE
			return if (page in 1..StorageSnapshots.ENDER_PAGES) page - 1 else NOT_STORAGE
		}
		if (backpackTitle.reset(text).matches()) {
			val slot = backpackTitle.group(1).toIntOrNull() ?: return NOT_STORAGE
			val page = slot - 1 + StorageSnapshots.ENDER_PAGES
			return if (page in StorageSnapshots.ENDER_PAGES until StorageSnapshots.PAGES) page else NOT_STORAGE
		}
		return NOT_STORAGE
	}

	internal fun navigateTo(page: Int) {
		Minecraft.getInstance().connection?.sendCommand(StorageSnapshots.command(page)) ?: return
		navigatingUntil = NanoClock.SYSTEM.nanoTime() + TRANSITION_NANOS
	}

	internal fun closing() {
		navigatingUntil = Long.MIN_VALUE
	}

	private fun remember(title: Component, stacks: List<ItemStack>, save: Boolean) {
		if (!SkyBlockLocation.inSkyBlock) return
		when (val page = storagePage(title)) {
			NOT_STORAGE -> return
			OVERVIEW -> StorageSnapshots.observeOverview(stacks)
			else -> StorageSnapshots.capture(page, stacks, save)
		}
	}

	private fun swap(event: GuiOpenEvent) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (!showOverlaySetting.on || !SkyBlockLocation.inSkyBlock) return
		val page = storagePage(screen.title)
		if (page == NOT_STORAGE) return
		event.screen = StorageOverlayScreen(screen, page, centered = navigating())
		navigatingUntil = Long.MIN_VALUE
	}

	private fun closed(event: GuiCloseEvent) {
		val overlay = event.screen as? StorageOverlayScreen ?: return
		if (navigating()) {
			val client = Minecraft.getInstance()
			client.execute { if (client.gui.screen() == null) client.gui.setScreen(overlay) }
			return
		}
		if (!retainScrollSetting.on) StorageOverlayScreen.forgetScroll()
	}

	internal fun navigating(): Boolean = NanoClock.SYSTEM.nanoTime() <= navigatingUntil

	private fun preview(event: TooltipEvent) {
		if (!previewSetting.on || !SkyBlockLocation.inSkyBlock) return
		if (storagePage(event.screen.title) != OVERVIEW) return
		if (!shiftHeld()) return
		if (event.hoveredSlot.container === Minecraft.getInstance().player?.inventory) return
		val page = StorageSnapshots.overviewPage(event.hoveredSlot.containerSlot)
		if (page == StorageSnapshots.NO_PAGE) return
		val items = StorageSnapshots.page(page) ?: return
		BackpackPreview.draw(event.graphics, StorageSnapshots.name(page), items, event.x, event.y)
		event.cancelled = true
	}

	private const val OVERVIEW_TITLE = "Storage"
}
