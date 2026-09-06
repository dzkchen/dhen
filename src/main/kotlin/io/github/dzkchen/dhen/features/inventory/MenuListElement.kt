package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.SLOT_BOX
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudElement
import io.github.dzkchen.dhen.ui.hud.HudLayout
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

internal const val NO_LINE = -1
internal const val DEFAULT_INK = 0

internal const val ALIGN_LEFT = 0
internal const val ALIGN_CENTER = 1
internal const val ALIGN_RIGHT = 2

internal class MenuLine {
	var text: String = ""
	var icon: ItemStack = ItemStack.EMPTY
	var slot: Int = NO_LINE
	var action: Int = NO_LINE
	var ink: Int = DEFAULT_INK

	fun reset(): MenuLine {
		text = ""
		icon = ItemStack.EMPTY
		slot = NO_LINE
		action = NO_LINE
		ink = DEFAULT_INK
		return this
	}
}

internal abstract class MenuListElement(
	name: String,
	anchor: HudAnchor,
	offsetX: Int,
	offsetY: Int
) : HudElement(name, anchor, offsetX, offsetY, inMenus = true) {
	private val memos = ArrayList<TextMemo>()
	private val pool = ArrayList<MenuLine>()

	protected val lines = ArrayList<MenuLine>()

	private var pointerX = 0
	private var pointerY = 0

	var hoveredLine: Int = NO_LINE
		private set

	protected open fun alignment(): Int = ALIGN_LEFT

	protected fun line(): MenuLine {
		val line = if (lines.size < pool.size) pool[lines.size] else MenuLine().also { pool += it }
		lines += line.reset()
		return line
	}

	fun clear() = clearLines()

	protected fun button(text: String, action: Int) {
		val line = line()
		line.text = text
		line.action = action
	}

	protected fun clearLines(retainHover: Boolean = false) {
		lines.clear()
		if (!retainHover) hoveredLine = NO_LINE
	}

	fun pointer(x: Int, y: Int) {
		pointerX = x
		pointerY = y
	}

	fun hoveredSlot(): Int = lines.getOrNull(hoveredLine)?.slot ?: NO_LINE

	fun hoveredAction(): Int = lines.getOrNull(hoveredLine)?.action ?: NO_LINE

	override val hasContent: Boolean
		get() = lines.isNotEmpty()

	override fun width(font: Font): Int {
		var widest = 0
		for (index in lines.indices) widest = maxOf(widest, memo(index).width(font, lines[index].text))
		return widest + ICON_ROOM + PADDING * 2
	}

	override fun height(font: Font): Int = lines.size * rowHeight(font) + PADDING * 2

	override fun render(graphics: GuiGraphicsExtractor, font: Font) {
		val width = width(font)
		val height = height(font)
		val row = rowHeight(font)
		GlassGui.roundedFrame(graphics, 0, 0, width, height, RADIUS, GlassGui.surface(), DhenPalette.BORDER)
		hoveredLine = hovered(graphics, font, width, height, row)
		for (index in lines.indices) {
			val line = lines[index]
			val top = PADDING + index * row
			if (index == hoveredLine && (line.slot != NO_LINE || line.action != NO_LINE)) {
				SharpGui.fill(graphics, PADDING, top - 1, width - PADDING, top + row - 1, DhenPalette.accentMuted)
			}
			if (!line.icon.isEmpty) ItemGui.stack(graphics, line.icon, PADDING, top - ICON_LIFT)
			val left = PADDING + ICON_ROOM
			val slack = width - PADDING - left - memo(index).width(font, line.text)
			val shift = when (alignment()) {
				ALIGN_CENTER -> slack / 2
				ALIGN_RIGHT -> slack
				else -> 0
			}
			val ink = if (line.ink == DEFAULT_INK) DhenPalette.TEXT_PRIMARY else line.ink
			memo(index).text(graphics, font, line.text, left + shift.coerceAtLeast(0), top, ink)
		}
	}

	override fun invalidateMeasurement() {
		for (memo in memos) memo.invalidate()
	}

	private fun hovered(graphics: GuiGraphicsExtractor, font: Font, width: Int, height: Int, row: Int): Int {
		val left = placeX(graphics.guiWidth(), HudLayout.scaled(width, scale))
		val top = placeY(graphics.guiHeight(), HudLayout.scaled(height, scale))
		val localX = ((pointerX - left) / scale).toInt()
		val localY = ((pointerY - top) / scale).toInt()
		if (localX < 0 || localX > width || localY < PADDING) return NO_LINE
		val index = (localY - PADDING) / row
		return if (index in lines.indices) index else NO_LINE
	}

	private fun memo(index: Int): TextMemo {
		while (memos.size <= index) memos += DhenType.memo()
		return memos[index]
	}

	protected open fun rowHeight(font: Font): Int = maxOf(DhenType.lineHeight(font), SLOT_BOX - ICON_SQUEEZE)

	private companion object {
		const val PADDING = 6
		const val ICON_ROOM = 12
		const val ICON_LIFT = 2
		const val ICON_SQUEEZE = 4
		const val RADIUS = 5f
	}
}
