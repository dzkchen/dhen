package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.IntSupplier

internal class PrefCard(section: PrefSection) {
	private val title = section.title

	val body = ControlBody(section.settings.mapNotNull(::controlFor))
	val height: Int
		get() = heightOf(body.height)

	fun invalidateMeasurements() = body.invalidateMeasurements()

	private fun heightOf(content: Int): Int =
		HEADER_HEIGHT + 2 * SECTION_PAD + if (content == 0) EMPTY_SECTION_HEIGHT else content

	fun draw(graphics: GuiGraphicsExtractor, font: Font, left: Int, top: Int, bottom: Int, mouseX: Int, mouseY: Int) {
		val content = body.height
		val right = left + PANEL_WIDTH
		val headerBottom = top + HEADER_HEIGHT
		val fill = GlassGui.raised()
		GlassGui.roundedFrame(graphics, left, top, right, top + heightOf(content), COLUMN_RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		ClickGuiPaint.headerBand(graphics, font, left, right, top, title, fill)
		ClickGuiPaint.headerRule(graphics, left, right, headerBottom, fill)
		val contentLeft = left + CONTENT_PAD
		var y = headerBottom + SECTION_PAD
		if (content == 0) {
			DhenType.text(graphics, font, EMPTY_SECTION_LABEL, contentLeft, textTop(font, y, EMPTY_SECTION_HEIGHT), DhenPalette.TEXT_DISABLED)
			return
		}
		val pointerY = if (mouseX in contentLeft until contentLeft + PANEL_CONTROLS_WIDTH) mouseY else NO_POINTER
		for (i in body.indices) {
			val control = body.at(i)
			val extent = control.extent
			if (extent == 0) continue
			if (y >= bottom) break
			control.draw(graphics, font, contentLeft, y, PANEL_CONTROLS_WIDTH, pointerY)
			y += extent
		}
	}
}

internal class ClickGuiPrefsPanel(
	private val viewportWidth: IntSupplier,
	private val viewportHeight: IntSupplier
) : ControlHost {
	private val cards: List<PrefCard> = ClientPrefs.sections.map(::PrefCard)
	private val stack = ScrollingStack(FIELD_TOP, SECTION_GAP, MARGIN, { cards.size }, viewportHeight, { index -> cards[index].height })

	private val fieldBottom: Int
		get() = viewportHeight.asInt - MARGIN

	fun reclamp() = stack.reclamp()

	fun scrollBy(delta: Int): Boolean = stack.scrollBy(delta)

	fun invalidateMeasurements() {
		for (i in cards.indices) cards[i].invalidateMeasurements()
	}

	override fun revealSpan(screenTop: Int, extent: Int) = stack.revealSpan(stack.localOf(screenTop), extent)

	fun draw(graphics: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		val left = panelLeft()
		val max = stack.max()
		val clipped = max > ClickGuiScroll.TOP
		val bottom = fieldBottom
		if (clipped) graphics.enableScissor(0, FIELD_TOP, viewportWidth.asInt, bottom)
		var top = FIELD_TOP - stack.offset
		for (i in cards.indices) {
			val card = cards[i]
			if (top >= bottom) break
			val cardHeight = card.height
			if (top + cardHeight > FIELD_TOP) card.draw(graphics, font, left, top, bottom, mouseX, mouseY)
			top += cardHeight + SECTION_GAP
		}
		if (!clipped) return
		graphics.disableScissor()
		ClickGuiPaint.scrollbar(graphics, left + PANEL_WIDTH, FIELD_TOP, bottom - FIELD_TOP, stack.offset, max)
	}

	fun controlAt(hit: ControlHit, x: Int, y: Int): SettingControl? {
		val left = panelLeft() + CONTENT_PAD
		if (x < left || x >= left + PANEL_CONTROLS_WIDTH) return null
		if (y !in FIELD_TOP..<fieldBottom) return null
		val slot = stack.slotAt(y)
		if (slot == ClickGuiShell.NONE) return null
		val body = cards[slot].body
		val top = stack.originOf(slot) + HEADER_HEIGHT + SECTION_PAD
		val index = body.indexAt(y - top)
		if (index == ClickGuiShell.NONE) return null
		return hit.record(this, left, top + body.topOf(index), PANEL_CONTROLS_WIDTH, body.at(index))
	}

	private fun panelLeft(): Int = ClickGuiShell.centeredLeft(viewportWidth.asInt, PANEL_WIDTH)
}
