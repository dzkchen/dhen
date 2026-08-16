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
	private var visibleCount = modules.size
	private var filtering = false
	private val settingsHeightAt = IntUnaryOperator { row -> settingsHeight(visibleRows[row]) }
	private val rowExtentAt = IntUnaryOperator { row -> ROW_HEIGHT + settingsHeightAt.applyAsInt(row) }
	private val rows = ScrollingStack(FIELD_TOP + HEADER_HEIGHT, NO_GAP, BODY_PAD, { rowCount }, fieldBottom, rowExtentAt)

	val hidden: Boolean
		get() = filtering && visibleCount == 0
	val rowCount: Int
		get() = if (isCollapsed) 0 else visibleCount
	val height: Int
		get() = shownHeight(naturalHeight)

	private val naturalHeight: Int
		get() = if (isCollapsed) HEADER_HEIGHT else HEADER_HEIGHT + BODY_PAD + rows.total()
	private val isCollapsed: Boolean
		get() = view.isCollapsed(category.name)

	private fun shownHeight(natural: Int): Int =
		ClickGuiShell.clampedColumnHeight(natural, HEADER_HEIGHT, fieldBottom.asInt - FIELD_TOP)

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
		rows.refilter()
	}

	fun reclamp() = rows.reclamp()

	fun scrollBy(delta: Int): Boolean = rows.scrollBy(delta)

	fun moduleAt(row: Int): Module = modules[visibleRows[row]]

	fun rowOf(module: Module?): Int {
		if (module == null || isCollapsed) return ClickGuiShell.NONE
		for (row in 0 until visibleCount) {
			if (modules[visibleRows[row]] === module) return row
		}
		return ClickGuiShell.NONE
	}

	fun revealRow(row: Int) = rows.reveal(row)

	override fun revealSpan(screenTop: Int, extent: Int) = rows.revealSpan(rows.localOf(screenTop), extent)

	fun rowAt(y: Int): Module? {
		val localY = bodyLocal(y) ?: return null
		val row = ClickGuiRows.rowAt(localY, visibleCount, ROW_HEIGHT, settingsHeightAt)
		return if (row == ClickGuiShell.NONE) null else modules[visibleRows[row]]
	}

	fun chevronContains(localX: Int): Boolean =
		localX >= COLUMN_WIDTH - CONTENT_PAD - glyphs.chevron - CHEVRON_HIT_SLOP

	fun settingsRowAt(y: Int): Int {
		val localY = bodyLocal(y) ?: return ClickGuiShell.NONE
		return ClickGuiRows.settingsRowAt(localY, visibleCount, ROW_HEIGHT, settingsHeightAt)
	}

	fun bodyOf(row: Int): ControlBody = controls[visibleRows[row]]

	fun bodyTop(row: Int): Int = rows.originOf(row) + ROW_HEIGHT + SETTINGS_PAD

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

	private fun bodyLocal(y: Int): Int? {
		if (y < FIELD_TOP + HEADER_HEIGHT || y >= FIELD_TOP + height) return null
		return rows.localOf(y)
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, mouseX: Int, mouseY: Int, focus: Module?) {
		val right = left + COLUMN_WIDTH
		val collapsedNow = isCollapsed
		val top = FIELD_TOP
		val shown = shownHeight(naturalHeight)
		val bodyTop = top + HEADER_HEIGHT
		val bottom = top + shown
		val overColumn = mouseX in left until right
		GlassGui.roundedFrame(graphics, left, top, right, bottom, COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER, HEADER_HEIGHT)
		val headerColor = if (overColumn && mouseY in top until bodyTop) GlassGui.interactive() else GlassGui.raised()
		val bodied = !collapsedNow && bottom > bodyTop
		ClickGuiPaint.headerBand(graphics, font, left, right, top, category.displayName, headerColor, bodied)
		val glyph = if (collapsedNow) EXPAND_GLYPH else COLLAPSE_GLYPH
		DhenType.text(graphics, font, glyph, right - CONTENT_PAD - glyphs.glyph, textTop(font, top, HEADER_HEIGHT), DhenPalette.TEXT_SECONDARY)
		if (!bodied) return
		ClickGuiPaint.headerRule(graphics, left, right, bodyTop)
		val max = rows.max()
		val clipped = max > ClickGuiScroll.TOP
		val visibleTop = bodyTop
		val visibleBottom = minOf(bottom, fieldBottom.asInt)
		val pointerY = if (overColumn && mouseY in visibleTop until visibleBottom) mouseY else NO_POINTER
		if (clipped) graphics.enableScissor(left + HAIRLINE_INSET, bodyTop, right - HAIRLINE_INSET, bottom)
		var rowTop = bodyTop - rows.offset
		for (row in 0 until visibleCount) {
			if (rowTop >= visibleBottom) break
			val index = visibleRows[row]
			val areaHeight = settingsHeight(index)
			val nextTop = rowTop + ROW_HEIGHT + areaHeight
			if (nextTop > visibleTop) {
				drawRow(graphics, font, modules[index], left, rowTop, pointerY, focus)
				if (areaHeight > 0) {
					drawSettings(graphics, font, index, left, rowTop + ROW_HEIGHT, areaHeight, visibleTop, visibleBottom, mouseX, mouseY)
				}
			}
			rowTop = nextTop
		}
		if (clipped) {
			graphics.disableScissor()
			ClickGuiPaint.scrollbar(graphics, right, bodyTop, shown - HEADER_HEIGHT, rows.offset, max)
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
		mouseY: Int
	) {
		SharpGui.fill(graphics, left + HAIRLINE_INSET, top, left + COLUMN_WIDTH - HAIRLINE_INSET, top + areaHeight, GlassGui.canvas())
		controls[index].draw(graphics, font, left + CONTENT_PAD, top + SETTINGS_PAD, CONTROLS_WIDTH, mouseX, mouseY, visibleTop, visibleBottom)
	}

	private fun settingsHeight(index: Int): Int {
		if (modules[index].name !in expanded) return 0
		return 2 * SETTINGS_PAD + controls[index].height
	}

	fun invalidateMeasurements() {
		for (i in controls.indices) controls[i].invalidateMeasurements()
	}
}
