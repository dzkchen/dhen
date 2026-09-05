package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.data.TabWidget
import io.github.dzkchen.dhen.data.TabWidgetState
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.FINAL_WORD
import io.github.dzkchen.dhen.event.TooltipEvent
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot

object CopyPlaytime : Module(
	name = "Copy Playtime",
	category = Category.INVENTORY,
	description = "Copies your playtime breakdown to the clipboard when you click it in the Detailed /playtime menu."
) {
	init {
		on<TooltipEvent>(FINAL_WORD) { hinted(it) }
		on<ContainerClickEvent> { clicked(it) }
	}

	private fun hinted(event: TooltipEvent) {
		if (!statsSlot(withoutCodes(event.screen.title.string), event.hoveredSlot)) return
		val lines = event.edit()
		lines.add(Component.empty())
		lines.add(DhenType.component(HINT))
	}

	private fun clicked(event: ContainerClickEvent) {
		val slot = event.hoveredSlot ?: return
		if (event.click.button() != LEFT_BUTTON) return
		if (!statsSlot(withoutCodes(event.screen.title.string), slot)) return
		event.cancelled = true
		val lore = SkyBlockItems.lore(slot.item)
		val text = StringBuilder(header())
		for (line in lore) text.append('\n').append(withoutCodes(line.string))
		Minecraft.getInstance().keyboardHandler.clipboard = text.toString()
		Dhen.announce(COPIED)
	}

	private fun header(): String {
		val profile = TabWidgetState.capture(TabWidget.PROFILE, PROFILE)?.trim().orEmpty()
		val named = profile.replaceFirstChar(Char::uppercaseChar)
		return "${Minecraft.getInstance().user.name}'s - $named Playtime Stats"
	}

	private fun statsSlot(title: String, slot: Slot?): Boolean =
		enabled && SkyBlockLocation.onHypixel && title == MENU_TITLE && slot != null && slot.index == STATS_SLOT

	private const val MENU_TITLE = "Detailed /playtime"
	private const val STATS_SLOT = 4
	private const val LEFT_BUTTON = 0
	private const val PROFILE = "profile"
	private const val HINT = "§eClick to Copy!"
	private const val COPIED = "Copied your playtime stats to the clipboard."
}
