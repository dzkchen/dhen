package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.centeredText
import io.github.dzkchen.dhen.gui.isPrintable
import io.github.dzkchen.dhen.gui.pillButton
import io.github.dzkchen.dhen.gui.textTop
import io.github.dzkchen.dhen.input.TextInputTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

internal class InventoryButtonsScreen(private val parent: Screen?) :
	LiveWorldScreen(DhenType.component(TITLE)), TextInputTarget, ContainerOrigin {
	private val titleMemo = DhenType.memo()
	private val bodyMemo = DhenType.memo()
	private val guideMemo = DhenType.memo()
	private val noticeMemo = DhenType.memo()
	private val labelMemos = Array(FIELD_COUNT) { DhenType.memo() }
	private val valueMemos = Array(FIELD_COUNT) { DhenType.memo() }
	private val actionMemos = Array(ACTION_COUNT) { DhenType.memo() }

	private var selected = InventoryButtons.NONE
	private var focused = NO_FOCUS

	override val textInputFocused: Boolean
		get() = focused != NO_FOCUS

	override fun dhenContainerLeft(): Int = (width - TOTAL_WIDTH) / 2 + FORM_WIDTH + FORM_GAP

	override fun dhenContainerTop(): Int = (height - BODY_HEIGHT) / 2

	override fun dhenContainerWidth(): Int = BODY_WIDTH

	override fun dhenContainerHeight(): Int = BODY_HEIGHT

	override fun dhenHoveredSlot(): Slot? = null

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		GlassGui.scrim(graphics, width, height)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val bodyLeft = dhenContainerLeft()
		val bodyTop = dhenContainerTop()
		val bodyRight = bodyLeft + BODY_WIDTH
		GlassGui.roundedFrame(graphics, bodyLeft, bodyTop, bodyRight, bodyTop + BODY_HEIGHT, BODY_RADIUS, GlassGui.canvas(), DhenPalette.BORDER)
		centeredText(graphics, font, bodyMemo, BODY_LABEL, bodyLeft, bodyRight, bodyTop + BODY_LABEL_TOP, DhenPalette.TEXT_DISABLED, TEXT_PAD)
		val hovered = InventoryButtons.hoveredIndex(this, mouseX, mouseY, selected)
		InventoryButtons.paint(graphics, this, InventoryButtons.NONE, hovered, selected, editing = true)
		drawForm(graphics, mouseX, mouseY)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
		val x = event.x().toInt()
		val y = event.y().toInt()
		val button = InventoryButtons.hoveredIndex(this, x, y, selected)
		if (button != InventoryButtons.NONE) {
			selected = button
			focused = NO_FOCUS
			return true
		}
		return clickedForm(x, y) || super.mouseClicked(event, doubleClick)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		when (event.key()) {
			GLFW.GLFW_KEY_ESCAPE -> when {
				focused != NO_FOCUS -> focused = NO_FOCUS
				selected != InventoryButtons.NONE -> selected = InventoryButtons.NONE
				else -> onClose()
			}

			GLFW.GLFW_KEY_TAB -> if (selected != InventoryButtons.NONE) {
				focused = (focused + 1) % FIELD_COUNT
			}

			GLFW.GLFW_KEY_BACKSPACE -> {
				val text = fieldText(focused) ?: return super.keyPressed(event)
				if (text.isNotEmpty()) write(text.substring(0, text.offsetByCodePoints(text.length, -1)))
			}

			else -> return super.keyPressed(event)
		}
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val text = fieldText(focused) ?: return super.charTyped(event)
		val codepoint = event.codepoint()
		if (!isPrintable(codepoint)) return super.charTyped(event)
		if (text.length >= MAX_FIELD) return true
		write(text + String(Character.toChars(codepoint)))
		return true
	}

	override fun onClose() {
		Minecraft.getInstance().gui.setScreen(parent)
	}

	override fun removed() {
		InventoryButtons.save()
		super.removed()
	}

	private fun drawForm(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val left = dhenContainerLeft() - FORM_GAP - FORM_WIDTH
		val right = left + FORM_WIDTH
		var top = dhenContainerTop()
		centeredText(graphics, font, titleMemo, TITLE, left, right, top, DhenPalette.accent, 0)
		top += HEADER_HEIGHT
		if (selected == InventoryButtons.NONE) {
			guideMemo.text(graphics, font, GUIDE, left, top, DhenPalette.TEXT_DISABLED)
			return
		}
		val button = InventoryButtons.all()[selected]
		for (field in 0 until FIELD_COUNT) {
			val rowTop = top + field * ROW_STEP
			labelMemos[field].text(graphics, font, FIELD_LABELS[field], left, rowTop, DhenPalette.TEXT_SECONDARY)
			drawField(graphics, field, left, right, rowTop + LABEL_HEIGHT)
		}
		var footTop = top + FIELD_COUNT * ROW_STEP
		if (!button.patternValid) {
			noticeMemo.text(graphics, font, BAD_PATTERN, left, footTop, DhenPalette.accent)
		}
		footTop += NOTICE_HEIGHT
		val disableLabel = if (button.disabled) ENABLE_LABEL else DISABLE_LABEL
		pillButton(graphics, font, actionMemos[ACTION_DISABLE], disableLabel, left, right, footTop, BUTTON_HEIGHT, mouseX, mouseY, TEXT_PAD)
		pillButton(
			graphics,
			font,
			actionMemos[ACTION_RESET],
			RESET_LABEL,
			left,
			right,
			footTop + BUTTON_HEIGHT + BUTTON_GAP,
			BUTTON_HEIGHT,
			mouseX,
			mouseY,
			TEXT_PAD
		)
	}

	private fun drawField(graphics: GuiGraphicsExtractor, field: Int, left: Int, right: Int, top: Int) {
		val active = focused == field
		RoundedGui.pillFrame(
			graphics,
			left,
			top,
			right,
			top + FIELD_HEIGHT,
			GlassGui.interactive(),
			if (active) DhenPalette.accent else DhenPalette.BORDER
		)
		val memo = valueMemos[field]
		val text = fieldText(field).orEmpty()
		val baseline = textTop(font, top, FIELD_HEIGHT)
		val shown = memo.fit(font, text, right - left - 2 * TEXT_PAD, fromEnd = true)
		memo.text(graphics, font, shown, left + TEXT_PAD, baseline, DhenPalette.TEXT_PRIMARY)
		if (!active) return
		val caretLeft = left + TEXT_PAD + memo.width(font, shown)
		SharpGui.fill(graphics, caretLeft, top + CARET_INSET, caretLeft + 1, top + FIELD_HEIGHT - CARET_INSET, DhenPalette.accent)
	}

	private fun clickedForm(x: Int, y: Int): Boolean {
		if (selected == InventoryButtons.NONE) return false
		val left = dhenContainerLeft() - FORM_GAP - FORM_WIDTH
		val right = left + FORM_WIDTH
		if (x !in left until right) return false
		val top = dhenContainerTop() + HEADER_HEIGHT
		focused = NO_FOCUS
		for (field in 0 until FIELD_COUNT) {
			val fieldTop = top + field * ROW_STEP + LABEL_HEIGHT
			if (y !in fieldTop until fieldTop + FIELD_HEIGHT) continue
			focused = field
			return true
		}
		val footTop = top + FIELD_COUNT * ROW_STEP + NOTICE_HEIGHT
		val button = InventoryButtons.all()[selected]
		if (y in footTop until footTop + BUTTON_HEIGHT) {
			button.disabled = !button.disabled
			return true
		}
		val resetTop = footTop + BUTTON_HEIGHT + BUTTON_GAP
		if (y !in resetTop until resetTop + BUTTON_HEIGHT) return false
		button.reset()
		return true
	}

	private fun fieldText(field: Int): String? {
		if (selected == InventoryButtons.NONE) return null
		val button = InventoryButtons.all()[selected]
		return when (field) {
			FIELD_ITEM -> button.item
			FIELD_COMMAND -> button.command
			FIELD_TITLE -> button.title
			FIELD_TOOLTIP -> button.tooltip
			else -> null
		}
	}

	private fun write(text: String) {
		val button = InventoryButtons.all()[selected]
		when (focused) {
			FIELD_ITEM -> button.item = text
			FIELD_COMMAND -> button.command = text
			FIELD_TITLE -> button.title = text
			FIELD_TOOLTIP -> button.tooltip = text
		}
	}

	private companion object {
		const val TITLE = "Inventory Buttons"
		const val BODY_LABEL = "Your menus open here"
		const val GUIDE = "Pick a button above or below."
		const val BAD_PATTERN = "That screen title is not a valid pattern."
		const val DISABLE_LABEL = "Hide this button"
		const val ENABLE_LABEL = "Show this button"
		const val RESET_LABEL = "Back to default"

		const val FIELD_ITEM = 0
		const val FIELD_COMMAND = 1
		const val FIELD_TITLE = 2
		const val FIELD_TOOLTIP = 3
		const val FIELD_COUNT = 4
		const val ACTION_DISABLE = 0
		const val ACTION_RESET = 1
		const val ACTION_COUNT = 2
		const val NO_FOCUS = -1

		val FIELD_LABELS = arrayOf("Icon", "Command", "Screen title", "Tooltip")

		const val BODY_WIDTH = 176
		const val BODY_HEIGHT = 222
		const val BODY_RADIUS = 6f
		const val BODY_LABEL_TOP = 12
		const val FORM_WIDTH = 140
		const val FORM_GAP = 12
		const val TOTAL_WIDTH = FORM_WIDTH + FORM_GAP + BODY_WIDTH
		const val HEADER_HEIGHT = 18
		const val ROW_STEP = 32
		const val LABEL_HEIGHT = 11
		const val FIELD_HEIGHT = 17
		const val BUTTON_HEIGHT = 20
		const val BUTTON_GAP = 6
		const val NOTICE_HEIGHT = 14
		const val TEXT_PAD = 6
		const val CARET_INSET = 4
		const val MAX_FIELD = 96
	}
}
