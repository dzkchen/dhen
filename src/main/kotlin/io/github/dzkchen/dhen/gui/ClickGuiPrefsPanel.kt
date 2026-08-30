package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.IntSupplier

internal class PrefCard(section: PrefSection) {
	private val title = section.title
	private val titleText = DhenType.memo()
	private val emptyText = DhenType.memo()

	val body = ControlBody(section.settings.mapNotNull(::controlFor))
	val height: Int
		get() = heightOf(body.height)

	fun measure(font: Font, width: Int): Boolean = body.measure(font, panelControlsWidth(width))

	fun invalidateMeasurements() {
		body.invalidateMeasurements()
		titleText.invalidate()
		emptyText.invalidate()
	}

	fun heightOf(content: Int): Int =
		HEADER_HEIGHT + 2 * SECTION_PAD + if (content == 0) EMPTY_SECTION_HEIGHT else content

	fun draw(
		graphics: GuiGraphicsExtractor,
		font: Font,
		left: Int,
		top: Int,
		content: Int,
		width: Int,
		visibleTop: Int,
		visibleBottom: Int,
		mouseX: Int,
		mouseY: Int,
		tooltip: ClickGuiTooltip
	) {
		val right = left + width
		val controlsWidth = panelControlsWidth(width)
		val headerBottom = top + HEADER_HEIGHT
		val fill = GlassGui.raised()
		GlassGui.roundedFrame(graphics, left, top, right, top + heightOf(content), COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER, HEADER_HEIGHT)
		ClickGuiPaint.headerBand(graphics, font, left, right, top, title, titleText, 0, fill, squared = true)
		ClickGuiPaint.headerRule(graphics, left, right, headerBottom)
		val contentLeft = left + CONTENT_PAD
		val contentTop = headerBottom + SECTION_PAD
		if (content == 0) {
			val shown = emptyText.fit(font, EMPTY_SECTION_LABEL, controlsWidth)
			emptyText.text(graphics, font, shown, contentLeft, textTop(font, contentTop, EMPTY_SECTION_HEIGHT), DhenPalette.TEXT_DISABLED)
			return
		}
		body.draw(
			graphics,
			font,
			contentLeft,
			contentTop,
			controlsWidth,
			mouseX,
			mouseY,
			visibleTop,
			visibleBottom,
			tooltip
		)
	}
}

internal class ClickGuiPrefsPanel(
	private val viewportWidth: IntSupplier,
	private val viewportHeight: IntSupplier
) : ControlHost {
	private val cards: List<PrefCard> = ClientPrefs.sections.map(::PrefCard)
	private val tooltip = ClickGuiTooltip()
	private val stack = ScrollingStack(FIELD_TOP, SECTION_GAP, MARGIN, { cards.size }, viewportHeight, { index -> cards[index].height })

	private val fieldBottom: Int
		get() = fieldBottomOf(viewportHeight.asInt)

	private val panelWidth: Int
		get() = chromeWidth(PANEL_WIDTH, viewportWidth.asInt)

	fun reclamp() = stack.reclamp()

	fun scrollBy(delta: Int): Boolean = stack.scrollBy(delta)

	fun measure(font: Font) {
		var changed = false
		for (i in cards.indices) {
			if (cards[i].measure(font, panelWidth)) changed = true
		}
		if (changed) stack.reclamp()
	}

	fun invalidateMeasurements(font: Font) {
		tooltip.invalidateMeasurement()
		for (i in cards.indices) cards[i].invalidateMeasurements()
		measure(font)
	}

	override fun revealSpan(screenTop: Int, extent: Int) = stack.revealSpan(stack.localOf(screenTop), extent)

	fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		measure(font)
		tooltip.clear()
		val left = panelLeft()
		val width = panelWidth
		val max = stack.max()
		val clipped = max > ClickGuiScroll.TOP
		val bottom = fieldBottom
		if (clipped) graphics.enableScissor(0, FIELD_TOP, viewportWidth.asInt, bottom)
		var top = FIELD_TOP - stack.offset
		for (i in cards.indices) {
			val card = cards[i]
			if (top >= bottom) break
			val content = card.body.height
			val cardHeight = card.heightOf(content)
			if (top + cardHeight > FIELD_TOP) {
				card.draw(graphics, font, left, top, content, width, FIELD_TOP, bottom, mouseX, mouseY, tooltip)
			}
			top += cardHeight + SECTION_GAP
		}
		if (clipped) {
			graphics.disableScissor()
			ClickGuiPaint.scrollbar(graphics, left + width, FIELD_TOP, bottom - FIELD_TOP, stack.offset, max)
		}
		tooltip.draw(graphics, font, viewportWidth.asInt, viewportHeight.asInt)
	}

	fun controlAt(hit: ControlHit, x: Int, y: Int): SettingControl? {
		val left = panelLeft() + CONTENT_PAD
		val controlsWidth = panelControlsWidth(panelWidth)
		if (x < left || x >= left + controlsWidth) return null
		if (y !in FIELD_TOP..<fieldBottom) return null
		val slot = stack.slotAt(y)
		if (slot == ClickGuiShell.NONE) return null
		val top = stack.originOf(slot) + HEADER_HEIGHT + SECTION_PAD
		return cards[slot].body.hit(hit, this, left, top, controlsWidth, y)
	}

	private fun panelLeft(): Int = ClickGuiShell.centeredLeft(viewportWidth.asInt, panelWidth)
}

internal fun panelControlsWidth(width: Int): Int = width - 2 * CONTENT_PAD
