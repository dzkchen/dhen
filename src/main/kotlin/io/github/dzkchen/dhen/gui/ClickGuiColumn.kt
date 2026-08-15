package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.IntSupplier
import java.util.function.IntUnaryOperator

internal class ColumnGlyphs {
	var glyph = 0
		private set
	var chevron = 0
		private set

	fun measure(font: Font) {
		glyph = maxOf(DhenType.width(font, EXPAND_GLYPH), DhenType.width(font, COLLAPSE_GLYPH))
		chevron = maxOf(DhenType.width(font, CHEVRON_COLLAPSED), DhenType.width(font, CHEVRON_EXPANDED))
	}
}

internal class ClickGuiColumn(
	val category: Category,
	private val modules: List<Module>,
	private val view: ClickGuiState,
	private val expanded: MutableSet<String>,
	private val glyphs: ColumnGlyphs,
	private val tooltip: ClickGuiTooltip,
	private val fieldBottom: IntSupplier
) : ControlHost {
	private val controls: List<ControlBody> = modules.map { module -> ControlBody(module.settings.mapNotNull(::controlFor)) }
	private val visibleRows = IntArray(modules.size) { it }
	private val rowTops = IntArray(modules.size + 1)
	private var visibleCount = modules.size
	private var filtering = false
	private val settingsHeightAt = IntUnaryOperator { row -> settingsHeight(visibleRows[row]) }
	private val scroll = ScrollState()

	var contentLeft = 0

	val hidden: Boolean
		get() = filtering && visibleCount == 0
	val navCount: Int
		get() = if (isCollapsed) 0 else visibleCount
	val height: Int
		get() = shownHeight(naturalHeight)

	private val naturalHeight: Int
		get() = if (isCollapsed) HEADER_HEIGHT else HEADER_HEIGHT + BODY_PAD + measureRows()
	private val isCollapsed: Boolean
		get() = view.isCollapsed(category.name)
	private val maxScroll: Int
		get() = naturalHeight.let { it - shownHeight(it) }
	private val columnViewport: Int
		get() = fieldBottom.asInt - FIELD_TOP

	private fun shownHeight(natural: Int): Int =
		ClickGuiShell.clampedColumnHeight(natural, HEADER_HEIGHT, columnViewport)

	fun applyFilter(query: String) {
		filtering = query.isNotEmpty()
		var count = 0
		for (i in modules.indices) {
			val module = modules[i]
			if (!ClickGuiSearch.matches(query, module.name, module.description)) continue
			visibleRows[count] = i
			count++
		}
		visibleCount = count
		scroll.refilter(maxScroll)
	}

	fun reclamp() = scroll.reclamp(maxScroll)

	fun scrollBy(delta: Int): Boolean {
		val max = maxScroll
		if (max <= ClickGuiScroll.TOP) return false
		scroll.scrollTo(scroll.offset - delta, max)
		return true
	}

	fun moduleAt(row: Int): Module = modules[visibleRows[row]]

	fun rowOf(module: Module?): Int {
		if (module == null || isCollapsed) return ClickGuiShell.NONE
		for (row in 0 until visibleCount) {
			if (modules[visibleRows[row]] === module) return row
		}
		return ClickGuiShell.NONE
	}

	private fun measureRows(): Int = ClickGuiRows.rowTops(visibleCount, ROW_HEIGHT, settingsHeightAt, rowTops)

	private fun measuredArea(row: Int): Int = rowTops[row + 1] - rowTops[row] - ROW_HEIGHT

	private fun rowTop(row: Int): Int = ClickGuiRows.rowTop(row, ROW_HEIGHT, settingsHeightAt)

	private fun rowExtent(row: Int): Int = ROW_HEIGHT + settingsHeight(visibleRows[row])

	fun revealRow(row: Int) = revealLocal(rowTop(row), rowExtent(row))

	override fun revealSpan(screenTop: Int, extent: Int) = revealLocal(localOf(screenTop), extent)

	private fun revealLocal(spanStart: Int, extent: Int) =
		scroll.reveal(spanStart, extent, height - HEADER_HEIGHT, maxScroll)

	fun rowAt(y: Int): Module? {
		val localY = bodyLocal(y) ?: return null
		val row = ClickGuiRows.rowAt(localY, visibleCount, ROW_HEIGHT, settingsHeightAt)
		return if (row == ClickGuiShell.NONE) null else modules[visibleRows[row]]
	}

	fun chevronContains(contentX: Int): Boolean =
		contentX >= contentLeft + COLUMN_WIDTH - CONTENT_PAD - glyphs.chevron - CHEVRON_HIT_SLOP

	fun settingsRowAt(y: Int): Int {
		val localY = bodyLocal(y) ?: return ClickGuiShell.NONE
		return ClickGuiRows.settingsRowAt(localY, visibleCount, ROW_HEIGHT, settingsHeightAt)
	}

	fun bodyOf(row: Int): ControlBody = controls[visibleRows[row]]

	fun bodyTop(row: Int): Int =
		FIELD_TOP + HEADER_HEIGHT - scroll.offset + ClickGuiRows.settingsTop(row, ROW_HEIGHT, settingsHeightAt) + SETTINGS_PAD

	fun toggleCollapsed() {
		view.toggle(category.name)
		reclamp()
	}

	fun toggleSettings(module: Module) {
		if (!expanded.remove(module.name)) expanded.add(module.name)
		reclamp()
	}

	fun collapseSettings(): Boolean {
		var changed = false
		for (row in 0 until visibleCount) {
			if (expanded.remove(modules[visibleRows[row]].name)) changed = true
		}
		if (changed) reclamp()
		return changed
	}

	private fun localOf(y: Int): Int = y - (FIELD_TOP + HEADER_HEIGHT) + scroll.offset

	private fun bodyLocal(y: Int): Int? {
		if (y < FIELD_TOP + HEADER_HEIGHT || y >= FIELD_TOP + height) return null
		return localOf(y)
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, mouseX: Int, mouseY: Int, focus: Module?) {
		val right = left + COLUMN_WIDTH
		val collapsedNow = isCollapsed
		val top = FIELD_TOP
		val natural = naturalHeight
		val shown = shownHeight(natural)
		val bodyTop = top + HEADER_HEIGHT
		val bottom = top + shown
		val overColumn = mouseX in left until right
		GlassGui.roundedFrame(graphics, left, top, right, bottom, COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		val headerColor = if (overColumn && mouseY in top until bodyTop) GlassGui.interactive() else GlassGui.raised()
		ClickGuiPaint.headerBand(graphics, font, left, right, top, category.displayName, headerColor)
		val glyph = if (collapsedNow) EXPAND_GLYPH else COLLAPSE_GLYPH
		DhenType.text(graphics, font, glyph, right - CONTENT_PAD - glyphs.glyph, textTop(font, top, HEADER_HEIGHT), DhenPalette.TEXT_SECONDARY)
		if (collapsedNow || bottom <= bodyTop) return
		ClickGuiPaint.headerRule(graphics, left, right, bodyTop, headerColor)
		val max = natural - shown
		val clipped = max > ClickGuiScroll.TOP
		val visibleTop = maxOf(bodyTop, FIELD_TOP)
		val visibleBottom = minOf(bottom, fieldBottom.asInt)
		val pointerY = if (overColumn && mouseY in visibleTop until visibleBottom) mouseY else NO_POINTER
		if (clipped) graphics.enableScissor(left + HAIRLINE_INSET, bodyTop, right - HAIRLINE_INSET, bottom)
		var rowTop = bodyTop - scroll.offset
		for (row in 0 until visibleCount) {
			if (rowTop >= visibleBottom) break
			val index = visibleRows[row]
			val areaHeight = measuredArea(row)
			val nextTop = rowTop + ROW_HEIGHT + areaHeight
			if (nextTop > visibleTop) {
				drawRow(graphics, font, modules[index], left, rowTop, pointerY, focus)
				if (areaHeight > 0) {
					drawSettings(graphics, font, index, left, rowTop + ROW_HEIGHT, areaHeight, visibleTop, visibleBottom, mouseX, pointerY)
				}
			}
			rowTop = nextTop
		}
		if (clipped) {
			graphics.disableScissor()
			ClickGuiPaint.scrollbar(graphics, right, bodyTop, shown - HEADER_HEIGHT, scroll.offset, max)
		}
	}

	private fun drawRow(
		graphics: GuiGraphicsExtractor,
		font: Font,
		module: Module,
		left: Int,
		rowTop: Int,
		pointerY: Int,
		focus: Module?
	) {
		val right = left + COLUMN_WIDTH
		val rowBottom = rowTop + ROW_HEIGHT
		val hovered = pointerY in rowTop until rowBottom
		val navigated = module === focus
		val active = hovered || navigated
		val railLeft = left + HAIRLINE_INSET
		if (active) SharpGui.fill(graphics, railLeft, rowTop, right - HAIRLINE_INSET, rowBottom, GlassGui.interactive())
		if (module.enabled) SharpGui.fill(graphics, railLeft, rowTop, railLeft + ROW_RAIL_WIDTH, rowBottom, DhenPalette.accent)
		if (navigated) {
			RoundedGui.border(graphics, railLeft, rowTop, right - HAIRLINE_INSET, rowBottom, FOCUS_RADIUS, RoundedGui.HAIRLINE, DhenPalette.accent)
		}

		val nameColor = when {
			module.enabled -> DhenPalette.accent
			active -> DhenPalette.TEXT_PRIMARY
			else -> DhenPalette.TEXT_SECONDARY
		}
		val labelTop = textTop(font, rowTop, ROW_HEIGHT)
		val nameLeft = left + ClickGuiShell.centeredLeft(COLUMN_WIDTH, DhenType.width(font, module.name))
		DhenType.text(graphics, font, module.name, nameLeft, labelTop, nameColor)

		val chevron = if (module.name in expanded) CHEVRON_EXPANDED else CHEVRON_COLLAPSED
		val chevronColor = if (hovered) DhenPalette.TEXT_SECONDARY else DhenPalette.TEXT_DISABLED
		DhenType.text(graphics, font, chevron, right - CONTENT_PAD - glyphs.chevron, labelTop, chevronColor)

		if (hovered && module.description.isNotEmpty()) tooltip.hover(module.description, left, rowTop)
	}

	private fun drawSettings(
		graphics: GuiGraphicsExtractor,
		font: Font,
		index: Int,
		left: Int,
		top: Int,
		areaHeight: Int,
		visibleTop: Int,
		visibleBottom: Int,
		mouseX: Int,
		pointerY: Int
	) {
		SharpGui.fill(graphics, left + HAIRLINE_INSET, top, left + COLUMN_WIDTH - HAIRLINE_INSET, top + areaHeight, GlassGui.canvas())
		val controlsLeft = left + CONTENT_PAD
		val controlPointer = if (mouseX in controlsLeft until controlsLeft + CONTROLS_WIDTH) pointerY else NO_POINTER
		var y = top + SETTINGS_PAD
		val body = controls[index]
		for (i in body.indices) {
			val control = body.at(i)
			val extent = control.extent
			if (extent == 0) continue
			if (y >= visibleBottom) break
			if (y + extent > visibleTop) control.draw(graphics, font, controlsLeft, y, CONTROLS_WIDTH, controlPointer)
			y += extent
		}
	}

	private fun settingsHeight(index: Int): Int {
		if (modules[index].name !in expanded) return 0
		return 2 * SETTINGS_PAD + controls[index].height
	}

	fun invalidateMeasurements() {
		for (i in controls.indices) controls[i].invalidateMeasurements()
	}

	fun resync(): Boolean {
		var changed = false
		for (i in controls.indices) {
			if (controls[i].resync()) changed = true
		}
		return changed
	}
}
