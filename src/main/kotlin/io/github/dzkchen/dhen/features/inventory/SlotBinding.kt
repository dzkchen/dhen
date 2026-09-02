package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.slotOutline
import io.github.dzkchen.dhen.input.keyHeld
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW
import kotlin.math.atan2
import kotlin.math.hypot

object SlotBinding : Module(
	name = "Slot Binding",
	category = Category.INVENTORY,
	description = "Links an inventory slot to a hotbar slot so one shift-click swaps them."
) {
	internal val bindKeySetting = KeybindSetting(
		"Binding Key",
		GLFW.GLFW_KEY_R,
		"Hold this and click a hotbar slot then an inventory slot to link them."
	)

	internal val showBoundSetting = BooleanSetting(
		"Show Bound Slots",
		default = true,
		description = "Draws the links you have made over the inventory screen."
	)

	internal val hoverOnlySetting = BooleanSetting(
		"Hover Only",
		description = "Only draws a link while the cursor sits on one of its two slots."
	).withDependency { showBoundSetting.on }

	internal val drawBorderSetting = BooleanSetting(
		"Draw Border",
		default = true,
		description = "Outlines both slots of every link."
	).withDependency { showBoundSetting.on }

	internal val drawLineSetting = BooleanSetting(
		"Draw Line",
		default = true,
		description = "Draws a line joining the two slots of every link."
	).withDependency { showBoundSetting.on }

	internal val borderColorSetting = ColorSetting(
		"Border Color",
		Color.rgba(255, 175, 175),
		description = "The colour of the outline around a linked slot."
	).withDependency { showBoundSetting.on && drawBorderSetting.on }

	internal val lineColorSetting = ColorSetting(
		"Line Color",
		Color.rgba(255, 255, 255),
		description = "The colour of the line joining two linked slots."
	).withDependency { showBoundSetting.on && drawLineSetting.on }

	internal var pending = ContainerState.NO_SLOT
		private set

	init {
		for (setting in listOf(
			bindKeySetting,
			showBoundSetting,
			hoverOnlySetting,
			drawBorderSetting,
			drawLineSetting,
			borderColorSetting,
			lineColorSetting
		)) registerSetting(setting)

		on<ContainerClickEvent> { event -> clicked(event) }
		on<ScreenRenderEvent.Post> { event -> draw(event.graphics, event.screen as? InventoryScreen ?: return@on) }
		on<GuiCloseEvent> { pending = ContainerState.NO_SLOT }
	}

	override fun onDisabled() {
		pending = ContainerState.NO_SLOT
	}

	internal fun edit(slot: Int) {
		val held = pending
		if (held == ContainerState.NO_SLOT) {
			if (ContainerState.partner(slot) == ContainerState.NO_SLOT) pending = slot
			else ContainerState.unbind(slot)
			return
		}
		pending = ContainerState.NO_SLOT
		if (held == slot) return
		val firstOnHotbar = held in ContainerState.HOTBAR_FIRST..ContainerState.HOTBAR_LAST
		val secondOnHotbar = slot in ContainerState.HOTBAR_FIRST..ContainerState.HOTBAR_LAST
		if (firstOnHotbar == secondOnHotbar) return
		ContainerState.bind(if (firstOnHotbar) slot else held, if (firstOnHotbar) held else slot)
	}

	private fun clicked(event: ContainerClickEvent) {
		if (event.screen !is InventoryScreen) return
		val slot = event.hoveredSlot ?: return
		if (keyHeld(bindKeySetting.code)) {
			event.cancelled = true
			edit(slot.index)
			return
		}
		if (event.click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return
		if (event.click.modifiers() and GLFW.GLFW_MOD_SHIFT == 0) return
		val partner = ContainerState.partner(slot.index)
		if (partner == ContainerState.NO_SLOT) return
		val menu = event.screen.menu
		if (!menu.carried.isEmpty) return
		if (locked(menu.slots.getOrNull(slot.index)) || locked(menu.slots.getOrNull(partner))) return
		val player = Minecraft.getInstance().player ?: return
		val gameMode = Minecraft.getInstance().gameMode ?: return
		val onHotbar = slot.index in ContainerState.HOTBAR_FIRST..ContainerState.HOTBAR_LAST
		val hotbarButton = (if (onHotbar) slot.index else partner) - ContainerState.HOTBAR_FIRST
		val sourceSlot = if (onHotbar) partner else slot.index
		event.cancelled = true
		gameMode.handleContainerInput(menu.containerId, sourceSlot, hotbarButton, ContainerInput.SWAP, player)
	}

	private fun locked(slot: Slot?): Boolean =
		slot != null && slot.container is Inventory && ProtectItem.locksSlot(slot.containerSlot)

	private fun draw(graphics: GuiGraphicsExtractor, screen: InventoryScreen) {
		val origin = screen as ContainerOrigin
		val left = origin.dhenContainerLeft()
		val top = origin.dhenContainerTop()
		if (pending != ContainerState.NO_SLOT) {
			outline(graphics, screen, pending, left, top, DhenPalette.accent)
		}
		if (!showBoundSetting.on) return
		val hovered = origin.dhenHoveredSlot()?.index ?: ContainerState.NO_SLOT
		for (index in ContainerState.HOTBAR_FIRST..ContainerState.HOTBAR_LAST) {
			val partner = ContainerState.partner(index)
			if (partner == ContainerState.NO_SLOT) continue
			if (hoverOnlySetting.on && hovered != index && hovered != partner) continue
			if (drawLineSetting.on) link(graphics, screen, index, partner, left, top)
			if (!drawBorderSetting.on) continue
			outline(graphics, screen, index, left, top, borderColorSetting.value.argb)
			outline(graphics, screen, partner, left, top, borderColorSetting.value.argb)
		}
	}

	private fun outline(
		graphics: GuiGraphicsExtractor,
		screen: AbstractContainerScreen<*>,
		index: Int,
		left: Int,
		top: Int,
		color: Int
	) {
		val slot = screen.menu.slots.getOrNull(index) ?: return
		slotOutline(graphics, left + slot.x, top + slot.y, color)
	}

	private fun link(
		graphics: GuiGraphicsExtractor,
		screen: AbstractContainerScreen<*>,
		from: Int,
		to: Int,
		left: Int,
		top: Int
	) {
		val start = screen.menu.slots.getOrNull(from) ?: return
		val end = screen.menu.slots.getOrNull(to) ?: return
		val startX = (left + start.x + SLOT_BOX / 2).toFloat()
		val startY = (top + start.y + SLOT_BOX / 2).toFloat()
		val deltaX = (end.x - start.x).toFloat()
		val deltaY = (end.y - start.y).toFloat()
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.translate(startX, startY)
			pose.rotate(atan2(deltaY, deltaX))
			SharpGui.fill(graphics, 0, 0, hypot(deltaX, deltaY).toInt(), EDGE, lineColorSetting.value.argb)
		} finally {
			pose.popMatrix()
		}
	}

	private const val EDGE = 1
}
