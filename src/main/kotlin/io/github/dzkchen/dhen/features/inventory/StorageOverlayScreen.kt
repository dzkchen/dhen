package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.isPrintable
import io.github.dzkchen.dhen.input.controlHeld
import io.github.dzkchen.dhen.input.shiftHeld
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

internal class StorageOverlayScreen(
	container: AbstractContainerScreen<*>,
	private val activePage: Int,
	centered: Boolean
) : LiveWorldScreen(container.title) {
	private val menu = container.menu
	private val nameMemos = Array(StorageSnapshots.PAGES) { DhenType.memo() }
	private val visible = IntArray(StorageSnapshots.PAGES)
	private val rowTop = IntArray(StorageSnapshots.PAGES)
	private val pageMatches = BooleanArray(StorageSnapshots.PAGES)
	private val slotMatches = Array(StorageSnapshots.PAGES) { BooleanArray(MAX_PAGE_SLOTS) }

	private var matchedQuery: String? = null
	private var visibleCount = 0
	private var contentHeight = 0
	private var columns = 1
	private var panelX = 0
	private var panelY = 0
	private var panelWidth = 0
	private var panelHeight = 0
	private var innerWidth = 0
	private var innerHeight = 0
	private var barX = 0
	private var playerX = 0
	private var playerY = 0
	private var knobGrabbed = false
	private val dragSlots = IntArray(MAX_DRAG_SLOTS)
	private var dragCount = 0
	private var dragButton = 0
	private var dragStart: Slot? = null
	private var dragLimit = 0
	private var dragBase = 0
	private var dragLeft = 0
	private var hoveredStack: ItemStack? = null
	private var hoveredSlot: Slot? = null
	private var hoveredPage = StorageSnapshots.NO_PAGE
	private var centeredOnActive = centered

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		GlassGui.scrim(graphics, width, height)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		if (!StorageOverlay.navigating() && minecraft.player?.containerMenu !== menu) {
			minecraft.execute { if (minecraft.gui.screen() === this) minecraft.gui.setScreen(null) }
			return
		}
		val scale = StorageOverlay.scaleSetting.amount.toFloat()
		val pose = graphics.pose()
		pose.pushMatrix()
		try {
			pose.scale(scale, scale)
			measure((width / scale).toInt(), (height / scale).toInt())
			collect()
			layout()
			if (!centeredOnActive) center()
			scroll = scroll.coerceIn(0f, maxScroll().toFloat())
			hoveredStack = null
			hoveredSlot = null
			hoveredPage = StorageSnapshots.NO_PAGE
			val virtualX = (mouseX / scale).toInt()
			val virtualY = (mouseY / scale).toInt()
			measureDrag()
			drawPages(graphics, virtualX, virtualY)
			drawScrollBar(graphics)
			drawPlayer(graphics, virtualX, virtualY)
			drawCarried(graphics, virtualX, virtualY)
			extendDrag()
		} finally {
			pose.popMatrix()
		}
		val hovered = hoveredStack
		ItemTooltip.slotChanged(if (hovered == null) ContainerState.NO_SLOT else System.identityHashCode(hovered))
		if (hovered != null) {
			graphics.setComponentTooltipForNextFrame(font, ItemTooltip.decorated(hovered), mouseX, mouseY)
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val scale = StorageOverlay.scaleSetting.amount.toFloat()
		val x = (event.x() / scale).toInt()
		val y = (event.y() / scale).toInt()
		if (x in barX until barX + SCROLL_BAR_WIDTH && y in panelY + PADDING until panelY + PADDING + innerHeight) {
			knobGrabbed = true
			scrollToKnob(y)
			return true
		}
		val slot = hoveredSlot
		if (slot != null) {
			val button = event.button()
			if (!doubleClick && !menu.carried.isEmpty && button <= GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				dragButton = button
				dragStart = slot
				dragSlots[0] = slot.index
				dragCount = 1
				return true
			}
			dispatch(slot, button, if (doubleClick) ContainerInput.PICKUP_ALL else null, event.hasShiftDown())
			return true
		}
		if (hoveredPage != StorageSnapshots.NO_PAGE && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			StorageOverlay.navigateTo(hoveredPage)
			return true
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		if (dragStart != null) return true
		if (!knobGrabbed) return super.mouseDragged(event, dragX, dragY)
		scrollToKnob((event.y() / StorageOverlay.scaleSetting.amount.toFloat()).toInt())
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		knobGrabbed = false
		val start = dragStart ?: return super.mouseReleased(event)
		if (dragCount >= MIN_DRAG_SLOTS) distribute() else dispatch(start, dragButton, null, event.hasShiftDown())
		dragStart = null
		dragCount = 0
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (scrollY == 0.0) return false
		if (hoveredStack != null && StorageOverlay.tooltipScrollSetting.on && ItemTooltip.scrolling()) {
			ItemTooltip.applyScroll(scrollY, shiftHeld(), controlHeld())
			return true
		}
		scroll = (scroll - (scrollY * StorageOverlay.scrollSpeedSetting.amount).toFloat())
			.coerceIn(0f, maxScroll().toFloat())
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (InventorySearch.enabled && InventorySearch.pressed(event.key(), event.hasControlDownWithQuirk())) return true
		if (minecraft.options.keyInventory.matches(event)) {
			onClose()
			return true
		}
		val slot = hoveredSlot ?: return super.keyPressed(event)
		val options = minecraft.options
		for (hotbar in options.keyHotbarSlots.indices) {
			if (!options.keyHotbarSlots[hotbar].matches(event)) continue
			dispatch(slot, hotbar, ContainerInput.SWAP, shift = false)
			return true
		}
		if (options.keySwapOffhand.matches(event)) {
			dispatch(slot, OFFHAND_BUTTON, ContainerInput.SWAP, shift = false)
			return true
		}
		if (options.keyDrop.matches(event)) {
			dispatch(slot, if (event.hasControlDown()) 1 else 0, ContainerInput.THROW, shift = false)
			return true
		}
		return super.keyPressed(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		if (!InventorySearch.focused || !isPrintable(codepoint)) return super.charTyped(event)
		InventorySearch.insert(codepoint.toChar())
		return true
	}

	override fun onClose() {
		StorageOverlay.closing()
		val player = minecraft.player
		if (player == null) super.onClose() else player.closeContainer()
	}

	private fun measure(virtualWidth: Int, virtualHeight: Int) {
		columns = StorageOverlay.columnsSetting.amount.toInt()
			.coerceAtMost((virtualWidth - PADDING) / (PAGE_WIDTH + PADDING))
			.coerceAtLeast(1)
		innerWidth = PAGE_WIDTH * columns + (columns - 1) * PADDING
		panelWidth = innerWidth + PADDING * 3 + SCROLL_BAR_WIDTH
		panelX = virtualWidth / 2 - panelWidth / 2
		panelHeight = minOf(
			virtualHeight - PLAYER_HEIGHT - minOf(HEIGHT_RELIEF, virtualHeight / HEIGHT_RELIEF_SHARE),
			StorageOverlay.maxHeightSetting.amount.toInt()
		).coerceAtLeast(EMPTY_PAGE_HEIGHT + PADDING * 2)
		innerHeight = panelHeight - PADDING * 2
		panelY = virtualHeight / 2 - (panelHeight + PLAYER_HEIGHT) / 2
		barX = panelX + PADDING + innerWidth + PADDING
		playerX = virtualWidth / 2 - PLAYER_WIDTH / 2
		playerY = panelY + panelHeight + PLAYER_GAP
	}

	private fun collect() {
		refreshMatches()
		visibleCount = 0
		for (page in 0 until StorageSnapshots.PAGES) {
			if (page != activePage && !StorageSnapshots.exists(page)) continue
			if (filtering() && !matched(page)) continue
			visible[visibleCount++] = page
		}
	}

	private fun layout() {
		contentHeight = 0
		var index = 0
		while (index < visibleCount) {
			val end = minOf(index + columns, visibleCount)
			var rowHeight = EMPTY_PAGE_HEIGHT
			for (probe in index until end) rowHeight = maxOf(rowHeight, heightOf(visible[probe]))
			for (probe in index until end) rowTop[probe] = contentHeight
			contentHeight += rowHeight
			index = end
		}
	}

	private fun center() {
		if (activePage == StorageOverlay.OVERVIEW) {
			centeredOnActive = true
			return
		}
		for (index in 0 until visibleCount) {
			if (visible[index] != activePage) continue
			val start = index / columns * columns
			var rowHeight = EMPTY_PAGE_HEIGHT
			for (probe in start until minOf(start + columns, visibleCount)) {
				rowHeight = maxOf(rowHeight, heightOf(visible[probe]))
			}
			scroll = (rowTop[index] + rowHeight / 2f - innerHeight / 2f).coerceIn(0f, maxScroll().toFloat())
			centeredOnActive = true
			return
		}
	}

	private fun maxScroll(): Int = (contentHeight + SCROLL_SLACK - innerHeight).coerceAtLeast(0)

	private fun heightOf(page: Int): Int {
		val rows = rowsOf(page)
		return if (rows == 0) EMPTY_PAGE_HEIGHT else rows * SLOT + PAGE_PAD + DhenType.lineHeight(font)
	}

	private fun rowsOf(page: Int): Int {
		if (page != activePage) return StorageSnapshots.rows(page)
		return ((chestSize() - StorageSnapshots.HEADER_SLOTS) / StorageSnapshots.ROW_WIDTH).coerceAtLeast(0)
	}

	private fun chestSize(): Int = menu.slots.size - PLAYER_SLOTS

	private fun drawPages(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val viewLeft = panelX + PADDING
		val viewTop = panelY + PADDING
		val viewBottom = viewTop + innerHeight
		GlassGui.roundedFrame(
			graphics,
			panelX,
			panelY,
			panelX + panelWidth,
			panelY + panelHeight,
			PANEL_RADIUS,
			GlassGui.canvas(),
			DhenPalette.BORDER
		)
		graphics.enableScissor(viewLeft, viewTop, viewLeft + innerWidth, viewBottom)
		val offset = scroll.toInt()
		val overView = inside(mouseX, mouseY, viewLeft, viewTop, innerWidth, innerHeight)
		val pointerX = if (overView) mouseX else OFF_PANEL
		val pointerY = if (overView) mouseY else OFF_PANEL
		for (index in 0 until visibleCount) {
			val page = visible[index]
			val left = viewLeft + index % columns * (PAGE_WIDTH + PADDING)
			val top = viewTop + rowTop[index] - offset
			val cellHeight = heightOf(page)
			if (top + cellHeight < viewTop || top > viewBottom) continue
			drawPage(graphics, page, left, top, cellHeight, pointerX, pointerY, viewTop, viewBottom)
		}
		graphics.disableScissor()
	}

	private fun drawPage(
		graphics: GuiGraphicsExtractor,
		page: Int,
		left: Int,
		top: Int,
		cellHeight: Int,
		mouseX: Int,
		mouseY: Int,
		viewTop: Int,
		viewBottom: Int
	) {
		val active = page == activePage
		RoundedGui.frame(
			graphics,
			left,
			top,
			left + PAGE_WIDTH,
			top + cellHeight,
			PAGE_RADIUS,
			GlassGui.surface(),
			if (active) DhenPalette.accent else DhenPalette.BORDER
		)
		val rows = rowsOf(page)
		val label = if (rows == 0) StorageSnapshots.unopenedName(page) else StorageSnapshots.name(page)
		nameMemos[page].text(
			graphics,
			font,
			label,
			left + LABEL_LEFT,
			top + LABEL_TOP,
			if (active) DhenPalette.accent else DhenPalette.TEXT_SECONDARY
		)
		if (!active && inside(mouseX, mouseY, left, top, PAGE_WIDTH, cellHeight)) hoveredPage = page
		if (rows == 0) return
		val slotsTop = top + LABEL_TOP + DhenType.lineHeight(font) + SLOTS_GAP
		val snapshot = if (active) null else StorageSnapshots.page(page)
		val count = rows * StorageSnapshots.ROW_WIDTH
		for (index in 0 until count) {
			val slotY = slotsTop + index / StorageSnapshots.ROW_WIDTH * SLOT
			if (slotY + SLOT_BOX < viewTop || slotY > viewBottom) continue
			val slotX = left + SLOT_LEFT + index % StorageSnapshots.ROW_WIDTH * SLOT
			val slot = if (active) liveSlot(index) else null
			val stack = slot?.item ?: snapshot?.getOrNull(index) ?: ItemStack.EMPTY
			val matching = if (slot == null) index < MAX_PAGE_SLOTS && slotMatches[page][index]
			else InventorySearch.matches(slot.index, slot.item)
			drawSlot(graphics, shownIn(slot, stack), slotX, slotY, matching, mouseX, mouseY)
			if (slot != null && hovering(mouseX, mouseY, slotX, slotY)) hoveredSlot = slot
		}
	}

	private fun liveSlot(index: Int): Slot? {
		val target = StorageSnapshots.HEADER_SLOTS + index
		return if (target < chestSize()) menu.slots[target] else null
	}

	private fun drawSlot(
		graphics: GuiGraphicsExtractor,
		stack: ItemStack,
		x: Int,
		y: Int,
		matching: Boolean,
		mouseX: Int,
		mouseY: Int
	) {
		val hovered = hovering(mouseX, mouseY, x, y)
		val backdrop = when {
			hovered -> GlassGui.interactive()
			matching -> InventorySearch.highlightSetting.value.argb
			else -> DhenPalette.SURFACE_RAISED
		}
		SharpGui.fill(graphics, x, y, x + SLOT_BOX, y + SLOT_BOX, backdrop)
		if (stack.isEmpty) return
		graphics.item(stack, x, y)
		graphics.itemDecorations(font, stack, x, y)
		if (hovered) hoveredStack = stack
	}

	private fun drawScrollBar(graphics: GuiGraphicsExtractor) {
		val top = panelY + PADDING
		RoundedGui.pill(graphics, barX, top, barX + SCROLL_BAR_WIDTH, top + innerHeight, DhenPalette.SURFACE)
		val span = contentHeight + SCROLL_SLACK
		val knobHeight = if (span <= innerHeight) innerHeight else
			(innerHeight.toLong() * innerHeight / span).toInt().coerceAtLeast(SCROLL_KNOB_MIN)
		val travel = innerHeight - knobHeight
		val max = maxScroll()
		val knobTop = if (max == 0) top else top + (travel * scroll / max).toInt()
		RoundedGui.pill(graphics, barX, knobTop, barX + SCROLL_BAR_WIDTH, knobTop + knobHeight, DhenPalette.accent)
	}

	private fun drawPlayer(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val first = chestSize()
		if (first < 0) return
		GlassGui.roundedFrame(
			graphics,
			playerX,
			playerY,
			playerX + PLAYER_WIDTH,
			playerY + PLAYER_HEIGHT,
			PANEL_RADIUS,
			GlassGui.canvas(),
			DhenPalette.BORDER
		)
		for (index in 0 until PLAYER_SLOTS) {
			val slot = menu.slots.getOrNull(first + index) ?: continue
			val row = index / StorageSnapshots.ROW_WIDTH
			val x = playerX + PLAYER_INSET + index % StorageSnapshots.ROW_WIDTH * SLOT
			val y = playerY + PLAYER_INSET + row * SLOT + if (row == HOTBAR_ROW) HOTBAR_GAP else 0
			drawSlot(graphics, shownIn(slot, slot.item), x, y, InventorySearch.matches(slot.index, slot.item), mouseX, mouseY)
			if (hovering(mouseX, mouseY, x, y)) hoveredSlot = slot
		}
	}

	private fun drawCarried(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val held = menu.carried
		if (held.isEmpty) return
		hoveredStack = null
		val carried = if (dragLimit == 0) held else held.copyWithCount(dragLeft)
		if (carried.isEmpty) return
		graphics.item(carried, mouseX - CARRIED_HALF, mouseY - CARRIED_HALF)
		graphics.itemDecorations(font, carried, mouseX - CARRIED_HALF, mouseY - CARRIED_HALF)
	}

	private fun measureDrag() {
		dragLimit = 0
		dragBase = 0
		dragLeft = 0
		if (dragStart == null || dragCount < MIN_DRAG_SLOTS) return
		val carried = menu.carried
		if (carried.isEmpty) return
		var eligible = 0
		var scanned = 0
		while (scanned < dragCount && eligible < carried.count) {
			if (draggable(slotAt(dragSlots[scanned]), carried)) eligible++
			scanned++
		}
		if (eligible < MIN_DRAG_SLOTS) return
		dragLimit = scanned
		dragBase = AbstractContainerMenu.getQuickCraftPlaceCount(eligible, dragButton, carried)
		var remaining = carried.count
		for (index in 0 until dragLimit) {
			val slot = slotAt(dragSlots[index]) ?: continue
			if (!draggable(slot, carried)) continue
			remaining -= shared(slot, carried) - slot.item.count
		}
		dragLeft = remaining.coerceAtLeast(0)
	}

	private fun extendDrag() {
		val slot = hoveredSlot ?: return
		if (dragStart == null || dragCount == MAX_DRAG_SLOTS) return
		for (index in 0 until dragCount) if (dragSlots[index] == slot.index) return
		dragSlots[dragCount++] = slot.index
	}

	private fun distribute() {
		val header = AbstractContainerMenu.QUICKCRAFT_HEADER_START
		clickSlot(menu, OUTSIDE_SLOT, AbstractContainerMenu.getQuickcraftMask(header, dragButton), ContainerInput.QUICK_CRAFT)
		val step = AbstractContainerMenu.getQuickcraftMask(AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, dragButton)
		for (index in 0 until dragCount) clickSlot(menu, dragSlots[index], step, ContainerInput.QUICK_CRAFT)
		val end = AbstractContainerMenu.getQuickcraftMask(AbstractContainerMenu.QUICKCRAFT_HEADER_END, dragButton)
		clickSlot(menu, OUTSIDE_SLOT, end, ContainerInput.QUICK_CRAFT)
	}

	private fun slotAt(index: Int): Slot? = menu.slots.getOrNull(index)

	private fun draggable(slot: Slot?, carried: ItemStack): Boolean =
		slot != null && slot.mayPlace(carried) && AbstractContainerMenu.canItemQuickReplace(slot, carried, true)

	private fun shared(slot: Slot, carried: ItemStack): Int =
		(slot.item.count + dragBase).coerceAtMost(minOf(carried.maxStackSize, slot.getMaxStackSize(carried)))

	private fun shownIn(slot: Slot?, stack: ItemStack): ItemStack {
		if (dragLimit == 0 || slot == null) return stack
		var position = 0
		while (position < dragLimit && dragSlots[position] != slot.index) position++
		if (position == dragLimit) return stack
		val carried = menu.carried
		if (!draggable(slot, carried)) return stack
		return carried.copyWithCount(shared(slot, carried))
	}

	private fun dispatch(slot: Slot, button: Int, forced: ContainerInput?, shift: Boolean) {
		val input = forced ?: if (shift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
		clickSlot(menu, slot.index, button, input)
	}

	private fun scrollToKnob(y: Int) {
		val top = panelY + PADDING
		val fraction = ((y - top).toFloat() / innerHeight).coerceIn(0f, 1f)
		scroll = maxScroll() * fraction
	}

	private fun filtering(): Boolean = StorageOverlay.hideNonMatchingSetting.on && InventorySearch.searching

	private fun refreshMatches() {
		val query = if (InventorySearch.searching) InventorySearch.query else null
		if (query == matchedQuery) return
		matchedQuery = query
		for (page in 0 until StorageSnapshots.PAGES) {
			val flags = slotMatches[page]
			flags.fill(false)
			var any = false
			val items = if (query == null) null else StorageSnapshots.page(page)
			if (items != null) {
				for (index in 0 until minOf(items.size, MAX_PAGE_SLOTS)) {
					val hit = InventorySearch.matches(ContainerState.NO_SLOT, items[index])
					flags[index] = hit
					any = any || hit
				}
			}
			pageMatches[page] = any
		}
	}

	private fun matched(page: Int): Boolean {
		if (page != activePage) return pageMatches[page]
		val last = chestSize()
		for (index in StorageSnapshots.HEADER_SLOTS until last) {
			val slot = menu.slots.getOrNull(index) ?: continue
			if (InventorySearch.matches(slot.index, slot.item)) return true
		}
		return false
	}

	private fun hovering(mouseX: Int, mouseY: Int, x: Int, y: Int): Boolean =
		inside(mouseX, mouseY, x, y, SLOT_BOX, SLOT_BOX)

	private fun inside(mouseX: Int, mouseY: Int, x: Int, y: Int, width: Int, height: Int): Boolean =
		mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

	internal companion object {
		const val SLOT = 17
		const val PADDING = 10
		const val PAGE_WIDTH = SLOT * StorageSnapshots.ROW_WIDTH + 4
		const val SCROLL_BAR_WIDTH = 8
		const val SCROLL_KNOB_MIN = 16
		const val SCROLL_SLACK = 6
		const val PLAYER_SLOTS = 36
		const val PLAYER_WIDTH = SLOT * StorageSnapshots.ROW_WIDTH + 6
		const val PLAYER_HEIGHT = SLOT * 4 + 18
		const val PLAYER_INSET = 3
		const val PLAYER_GAP = 2
		const val HOTBAR_ROW = 3
		const val HOTBAR_GAP = 4
		const val HEIGHT_RELIEF = 80
		const val HEIGHT_RELIEF_SHARE = 10
		const val EMPTY_PAGE_HEIGHT = 18
		const val PAGE_PAD = 6
		const val PAGE_RADIUS = 3f
		const val PANEL_RADIUS = 5f
		const val LABEL_LEFT = 4
		const val LABEL_TOP = 3
		const val SLOTS_GAP = 2
		const val SLOT_LEFT = 3
		const val CARRIED_HALF = 8
		const val MAX_PAGE_SLOTS = 45
		const val OFFHAND_BUTTON = 40
		const val MAX_DRAG_SLOTS = 64
		const val MIN_DRAG_SLOTS = 2
		const val OUTSIDE_SLOT = AbstractContainerMenu.SLOT_CLICKED_OUTSIDE
		const val OFF_PANEL = Int.MIN_VALUE

		private var scroll = 0f

		fun forgetScroll() {
			scroll = 0f
		}
	}
}
