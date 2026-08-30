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
		visibleTop: Int,
		visibleBottom: Int,
		mouseX: Int,
		mouseY: Int,
		tooltip: ClickGuiTooltip
	) {
		val right = left + PANEL_WIDTH
		val headerBottom = top + HEADER_HEIGHT
		val fill = GlassGui.raised()
		GlassGui.roundedFrame(graphics, left, top, right, top + heightOf(content), COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER, HEADER_HEIGHT)
		ClickGuiPaint.headerBand(graphics, font, left, right, top, title, titleText, 0, fill, squared = true)
		ClickGuiPaint.headerRule(graphics, left, right, headerBottom)
		val contentLeft = left + CONTENT_PAD
		val contentTop = headerBottom + SECTION_PAD
		if (content == 0) {
			val shown = emptyText.fit(font, EMPTY_SECTION_LABEL, PANEL_CONTROLS_WIDTH)
			emptyText.text(graphics, font, shown, contentLeft, textTop(font, contentTop, EMPTY_SECTION_HEIGHT), DhenPalette.TEXT_DISABLED)
			return
		}
		body.draw(
			graphics,
			font,
			contentLeft,
			contentTop,
			PANEL_CONTROLS_WIDTH,
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

	fun reclamp() = stack.reclamp()

	fun scrollBy(delta: Int): Boolean = stack.scrollBy(delta)

	fun invalidateMeasurements() {
		tooltip.invalidateMeasurement()
		for (i in cards.indices) cards[i].invalidateMeasurements()
	}

	override fun revealSpan(screenTop: Int, extent: Int) = stack.revealSpan(stack.localOf(screenTop), extent)

	fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		tooltip.clear()
		val left = panelLeft()
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
				card.draw(graphics, font, left, top, content, FIELD_TOP, bottom, mouseX, mouseY, tooltip)
			}
			top += cardHeight + SECTION_GAP
		}
		if (clipped) {
			graphics.disableScissor()
			ClickGuiPaint.scrollbar(graphics, left + PANEL_WIDTH, FIELD_TOP, bottom - FIELD_TOP, stack.offset, max)
		}
		tooltip.draw(graphics, font, viewportWidth.asInt, viewportHeight.asInt)
	}

	fun controlAt(hit: ControlHit, x: Int, y: Int): SettingControl? {
		val left = panelLeft() + CONTENT_PAD
		if (x < left || x >= left + PANEL_CONTROLS_WIDTH) return null
		if (y !in FIELD_TOP..<fieldBottom) return null
		val slot = stack.slotAt(y)
		if (slot == ClickGuiShell.NONE) return null
		val top = stack.originOf(slot) + HEADER_HEIGHT + SECTION_PAD
		return cards[slot].body.hit(hit, this, left, top, PANEL_CONTROLS_WIDTH, y)
	}

	private fun panelLeft(): Int = ClickGuiShell.centeredLeft(viewportWidth.asInt, PANEL_WIDTH)
}
