package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.price.PriceSource
import io.github.dzkchen.dhen.data.value.ItemValue
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GradientGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.input.keyHeld
import io.github.dzkchen.dhen.util.shortNumber
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import net.minecraft.world.entity.EquipmentSlot
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil

internal class WardrobeScreen(val container: AbstractContainerScreen<*>) : LiveWorldScreen(container.title) {
	private val memos = Array(WARDROBE_SETS) { DhenType.memo() }
	private val buttonMemos = Array(4) { DhenType.memo() }
	private val heading = DhenType.memo()
	private val statusMemo = DhenType.memo()
	private val heartMemos = Array(WARDROBE_SETS) { DhenType.memo() }
	private val statusMemos = Array(WARDROBE_SETS) { DhenType.memo() }
	private val priceMemo = DhenType.memo()
	private val previews = arrayOfNulls<RemotePlayer>(WARDROBE_SETS)
	private val values = Array<List<Component>>(WARDROBE_SETS) { emptyList() }
	private val labels = Array(WARDROBE_SETS) { "Set ${it + 1}" }
	private val visible = IntArray(WARDROBE_SETS)
	private var count = 0
	private var revision = -1
	private var scale = 1f
	private var left = 0
	private var top = 0
	private var panelWidth = 0
	private var panelHeight = 0
	private var rowCount = 0
	private var buttonTop = 0
	private var hovered = -1
	private var hoveredPart = -1
	private var favoriteHover = false
	private var priceHover = false
	private var ticks = 0

	override fun tick() {
		if (minecraft.player?.containerMenu !== container.menu) {
			minecraft.gui.setScreen(null)
			return
		}
		if (revision == CustomWardrobe.revision && ++ticks < PRICE_REFRESH_TICKS) return
		ticks = 0
		revision = CustomWardrobe.revision
		val level = minecraft.level ?: return
		val profile = minecraft.player?.gameProfile ?: return
		val model = CustomWardrobe.model
		for (index in 0 until WARDROBE_SETS) {
			if (model.empty(index)) {
				previews[index] = null
				values[index] = emptyList()
				continue
			}
			val preview = previews[index] ?: RemotePlayer(level, profile).also { previews[index] = it }
			val lore = ArrayList<Component>()
			lore += DhenType.component("Estimated Armor Value:")
			var total = 0.0
			for (part in 0 until 4) {
				val stack = model.pieces[index][part]
				val clean = stack.copy()
				clean.remove(DataComponents.ENCHANTMENTS)
				preview.setItemSlot(EQUIPMENT[part], clean)
				if (stack.isEmpty) continue
				val price = ItemValue.of(stack, PriceSource.LOWEST_BIN).total
				total += price
				lore += DhenType.component("${stack.hoverName.string}: ${shortNumber(price.toLong())}")
			}
			lore += DhenType.component("Total: ${shortNumber(total.toLong())}")
			values[index] = lore
		}
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val config = CustomWardrobe
		val model = config.model
		count = 0
		for (index in 0 until WARDROBE_SETS) if (model.visible(index, config.hideLocked.on, config.hideEmpty.on, config.onlyFavorites.on)) visible[count++] = index
		val columns = minOf(maxOf(count, 1), config.perRow.amount.toInt())
		rowCount = maxOf(1, ceil(count.toDouble() / columns).toInt())
		val cardWidth = config.slotWidth.amount.toInt()
		val cardHeight = config.slotHeight.amount.toInt()
		val gapX = config.horizontal.amount.toInt()
		val gapY = config.vertical.amount.toInt()
		val pad = config.padding.amount.toInt()
		val buttonWidth = config.buttonWidth.amount.toInt()
		val buttonHeight = config.buttonHeight.amount.toInt()
		val buttonGap = config.buttonHorizontal.amount.toInt()
		panelWidth = maxOf(columns * (cardWidth + gapX) - gapX, 2 * buttonWidth + buttonGap) + pad * 2
		panelHeight = rowCount * (cardHeight + gapY) - gapY + config.slotsToButtons.amount.toInt() + buttonHeight * 2 + config.buttonVertical.amount.toInt() + pad * 2 + HEADER
		scale = minOf(config.globalScale.amount.toFloat() / 100f, width * FIT_MARGIN / panelWidth, height * FIT_MARGIN / panelHeight)
		left = ((width / scale - panelWidth) / 2).toInt()
		top = ((height / scale - panelHeight) / 2).toInt()
		val x = (mouseX / scale).toInt()
		val y = (mouseY / scale).toInt()
		hovered = -1
		hoveredPart = -1
		favoriteHover = false
		priceHover = false
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.scale(scale, scale)
			SharpGui.fill(graphics, left, top, left + panelWidth, top + panelHeight, config.background.value.argb)
			heading.text(graphics, font, "Custom Wardrobe", left + pad, top + pad, DhenPalette.TEXT_PRIMARY)
			for (shown in 0 until count) {
				val index = visible[shown]
				val cardX = left + pad + shown % columns * (cardWidth + gapX)
				val cardY = top + pad + HEADER + shown / columns * (cardHeight + gapY)
				drawCard(graphics, index, cardX, cardY, cardWidth, cardHeight, x, y)
			}
			if (count == 0) statusMemo.text(graphics, font, "No sets match these filters.", left + pad, top + pad + HEADER, DhenPalette.TEXT_SECONDARY)
			buttonTop = top + pad + HEADER + rowCount * (cardHeight + gapY) - gapY + config.slotsToButtons.amount.toInt()
			for (index in BUTTONS.indices) {
				val buttonX = left + (panelWidth - 2 * buttonWidth - buttonGap) / 2 + index % 2 * (buttonWidth + buttonGap)
				val buttonY = buttonTop + index / 2 * (buttonHeight + config.buttonVertical.amount.toInt())
				SharpGui.fill(graphics, buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, DhenPalette.SURFACE_RAISED)
				buttonMemos[index].text(graphics, font, if (index == 1 && config.onlyFavorites.on) "All Sets" else BUTTONS[index], buttonX + 2, buttonY + 2, DhenPalette.TEXT_PRIMARY)
			}
			if (model.waiting && config.loadingText.on) statusMemo.text(graphics, font, "Loading...", left + panelWidth - pad - statusMemo.width(font, "Loading..."), top + pad, DhenPalette.SLOT_RED)
		} finally {
			pose.popMatrix()
		}
		if (hovered < 0) return
		if (priceHover) graphics.setComponentTooltipForNextFrame(font, values[hovered], mouseX, mouseY)
		else if (hoveredPart >= 0 && (!config.tooltipRequired.on || tooltipHeld(config.tooltipKey.code))) {
			val stack = model.pieces[hovered][hoveredPart]
			if (!stack.isEmpty) graphics.setComponentTooltipForNextFrame(font, ItemTooltip.decorated(stack), mouseX, mouseY)
		}
	}

	private fun tooltipHeld(code: Int): Boolean =
		if (code in GLFW.GLFW_MOUSE_BUTTON_1..GLFW.GLFW_MOUSE_BUTTON_LAST) {
			GLFW.glfwGetMouseButton(minecraft.window.handle(), code) == GLFW.GLFW_PRESS
		} else keyHeld(code)

	private fun drawCard(graphics: GuiGraphicsExtractor, index: Int, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int) {
		val config = CustomWardrobe
		val model = config.model
		val color = when {
			model.equipped == index -> config.equippedColor.value.argb
			model.favorites[index] -> config.favoriteColor.value.argb
			index / WARDROBE_PAGE_SIZE == model.page -> config.samePage.value.argb
			else -> config.otherPage.value.argb
		}
		SharpGui.fill(graphics, x, y, x + width, y + height, color)
		val hit = mouseX in x until x + width && mouseY in y until y + height
		if (hit) {
			hovered = index
			favoriteHover = ((!model.empty(index) && !model.locked[index]) || model.favorites[index]) && mouseY < y + HEADER && mouseX >= x + width - HEADER
			priceHover = config.estimatedValue.on && !model.empty(index) && !model.locked[index] && mouseY < y + HEADER && mouseX < x + HEADER
			hoveredPart = ((mouseY - y - HEADER) * 4 / maxOf(1, height - HEADER)).coerceIn(0, 3)
			val thickness = config.outlineThickness.amount.toInt()
			for (distance in thickness downTo 1) {
				val fade = (distance - 1).toDouble() / maxOf(1, thickness)
				val opacity = 1.0 - config.outlineBlur.amount * fade
				val topColor = config.outlineTop.value.argb
				val bottomColor = config.outlineBottom.value.argb
				val upper = DhenPalette.withAlpha(topColor, ((topColor ushr 24) * opacity).toInt())
				val lower = DhenPalette.withAlpha(bottomColor, ((bottomColor ushr 24) * opacity).toInt())
				GradientGui.vertical(graphics, x - distance, y, x - distance + 1, y + height, upper, lower)
				GradientGui.vertical(graphics, x + width + distance - 1, y, x + width + distance, y + height, upper, lower)
				SharpGui.fill(graphics, x - distance, y - distance, x + width + distance, y - distance + 1, upper)
				SharpGui.fill(graphics, x - distance, y + height + distance - 1, x + width + distance, y + height + distance, lower)
			}
		}
		memos[index].text(graphics, font, labels[index], x + (width - memos[index].width(font, labels[index])) / 2, y + 3, DhenPalette.TEXT_PRIMARY)
		if ((!model.empty(index) && !model.locked[index]) || model.favorites[index]) heartMemos[index].text(graphics, font, if (model.favorites[index]) "♥" else "♡", x + width - 10, y + 3, DhenPalette.SLOT_RED)
		if (config.estimatedValue.on && !model.empty(index) && !model.locked[index]) priceMemo.text(graphics, font, "$", x + 3, y + 3, DhenPalette.SLOT_GREEN)
		val preview = previews[index]
		if (preview != null && config.playerScale.amount > 0) {
			val trackingX = if (config.followMouse.on) mouseX.toFloat() else (x + width / 2).toFloat()
			val trackingY = if (config.followMouse.on) mouseY.toFloat() else (y + height / 2).toFloat()
			val pageScale = if (index / WARDROBE_PAGE_SIZE == model.page) 1.0 else OTHER_PAGE_SCALE
			val entityScale = (config.playerScale.amount * width / PLAYER_SCALE_DIVISOR * pageScale * scale).toInt()
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
				(x * scale).toInt(), ((y + HEADER) * scale).toInt(), ((x + width) * scale).toInt(), ((y + height) * scale).toInt(),
				entityScale, ENTITY_OFFSET * scale, trackingX * scale, trackingY * scale, preview)
		} else if (model.locked[index] || model.empty(index)) {
			val label = if (!model.known[index]) "Not loaded" else if (model.locked[index]) "Locked" else "Empty"
			statusMemos[index].text(graphics, font, label, x + 3, y + height / 2, DhenPalette.TEXT_SECONDARY)
		}
		for (part in 0 until 4) {
			val item = model.pieces[index][part]
			if (!item.isEmpty) ItemGui.stack(graphics, item, x + 2, y + HEADER + part * maxOf(1, (height - HEADER) / 4))
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (bound(event.button(), true)) return true
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
		if (hovered >= 0) {
			if (favoriteHover) CustomWardrobe.favorite(hovered) else if (!priceHover) select(hovered)
			return true
		}
		val config = CustomWardrobe
		val width = config.buttonWidth.amount.toInt()
		val height = config.buttonHeight.amount.toInt()
		val gap = config.buttonHorizontal.amount.toInt()
		val x = (event.x() / scale).toInt()
		val y = (event.y() / scale).toInt()
		for (index in BUTTONS.indices) {
			val buttonX = left + (panelWidth - 2 * width - gap) / 2 + index % 2 * (width + gap)
			val buttonY = buttonTop + index / 2 * (height + config.buttonVertical.amount.toInt())
			if (x !in buttonX until buttonX + width || y !in buttonY until buttonY + height) continue
			when (index) {
				0 -> config.swap(container, true)
				1 -> config.filter()
				2 -> click(48)
				3 -> onClose()
			}
			return true
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (bound(event.key(), false)) return true
		if (minecraft.options.keyInventory.matches(event)) {
			onClose()
			return true
		}
		return super.keyPressed(event)
	}

	private fun bound(code: Int, mouse: Boolean): Boolean {
		if (!CustomWardrobe.numberKeys.on) return false
		val index = MenuKeybinds.boundIndex(code, mouse, WardrobeKeybinds.slotSettings)
		if (index < 0) return false
		if (MenuKeybinds.heldDown(this, code, mouse, Util.getMillis())) return true
		val model = CustomWardrobe.model
		var boundIndex = 0
		for (shown in 0 until count) {
			val target = visible[shown]
			if (target / WARDROBE_PAGE_SIZE != model.page) continue
			if (boundIndex++ == index) {
				select(target)
				break
			}
		}
		return true
	}

	private fun select(index: Int) {
		val model = CustomWardrobe.model
		if (model.waiting) return
		val page = index / WARDROBE_PAGE_SIZE
		if (page != model.page) {
			model.waitingPage = model.page + if (page > model.page) 1 else -1
			if (model.waitingPage !in 0 until model.pages) return
			if (click(if (page > model.page) 53 else 45)) model.waiting = true
		} else if (!model.locked[index] && !model.empty(index)) click(36 + index % WARDROBE_PAGE_SIZE)
	}

	private fun click(slot: Int): Boolean {
		if (minecraft.player?.containerMenu !== container.menu || CustomWardrobe.model.window != container.menu.containerId) return false
		MenuKeybinds.click(container, slot)
		return true
	}

	override fun onClose() {
		CustomWardrobe.model.leave()
		minecraft.player?.closeContainer()
		super.onClose()
	}

	private companion object {
		val EQUIPMENT = arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
		val BUTTONS = arrayOf("Edit", "Favorites", "Back", "Close")
		const val HEADER = 16
		const val PRICE_REFRESH_TICKS = 100
		const val FIT_MARGIN = 0.95f
		const val PLAYER_SCALE_DIVISOR = 100.0
		const val OTHER_PAGE_SCALE = 0.9
		const val ENTITY_OFFSET = 0.0625f
	}
}
