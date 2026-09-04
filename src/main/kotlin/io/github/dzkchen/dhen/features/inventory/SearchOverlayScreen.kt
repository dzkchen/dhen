package io.github.dzkchen.dhen.features.inventory

import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.SearchName
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.ItemGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.SharpGui
import io.github.dzkchen.dhen.gui.TextMemo
import io.github.dzkchen.dhen.gui.isPrintable
import io.github.dzkchen.dhen.gui.textTop
import io.github.dzkchen.dhen.input.TextInputTarget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import org.lwjgl.glfw.GLFW

internal class SearchOverlayScreen(
	private val pos: BlockPos,
	private val frontText: Boolean,
	private val lines: Array<String>,
	private val place: SearchPlace,
	typed: String
) : LiveWorldScreen(DhenType.component(TITLE)), TextInputTarget {
	private val headerMemo = DhenType.memo()
	private val inputMemo = DhenType.memo()
	private val recentMemo = DhenType.memo()
	private val rowMemos = Array(MAX_ROWS) { DhenType.memo() }

	private val matches = ArrayList<SearchName>(MAX_ROWS)
	private var history = SearchOverlay.history(place)
	private var input = typed
	private var pushed = false

	override val textInputFocused: Boolean
		get() = true

	init {
		SearchOverlay.suggestions(input, matches)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val left = width / 2 - PANEL_HALF_WIDTH
		val top = panelTop()
		val bottom = top + panelHeight()
		GlassGui.roundedFrame(graphics, left, top, width / 2 + PANEL_HALF_WIDTH, bottom, PANEL_RADIUS, GlassGui.canvas(), DhenPalette.BORDER)
		headerMemo.text(graphics, font, place.label, left + PAD, textTop(font, top, ROW), DhenPalette.TEXT_SECONDARY)
		drawInput(graphics, left, top + ROW)
		var row = top + ROW * 2
		for (index in matches.indices) {
			drawRow(graphics, rowMemos[index], matches[index].label, matches[index].id, left, row, mouseX, mouseY)
			row += ROW
		}
		if (history.isEmpty()) return
		recentMemo.text(graphics, font, RECENT_LABEL, left + PAD, textTop(font, row, RECENT_HEIGHT), DhenPalette.TEXT_SECONDARY)
		row += RECENT_HEIGHT
		for (index in history.indices) {
			drawRow(graphics, rowMemos[matches.size + index], history[index], "", left, row, mouseX, mouseY)
			row += ROW
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val left = width / 2 - PANEL_HALF_WIDTH
		val x = event.x().toInt()
		val y = event.y().toInt()
		if (x !in left + PAD until left + PAD + ROW_WIDTH) return super.mouseClicked(event, doubleClick)
		val suggestionsTop = panelTop() + ROW * 2
		val hit = (y - suggestionsTop) / ROW
		if (y >= suggestionsTop && hit in matches.indices) {
			input = matches[hit].label
			onClose()
			return true
		}
		val historyTop = suggestionsTop + matches.size * ROW + RECENT_HEIGHT
		val past = (y - historyTop) / ROW
		if (y < historyTop || past !in history.indices) return super.mouseClicked(event, doubleClick)
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			SearchOverlay.forget(place, history[past])
			history = SearchOverlay.history(place)
			return true
		}
		input = history[past]
		onClose()
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		when (event.key()) {
			GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				onClose()
				return true
			}

			GLFW.GLFW_KEY_ESCAPE -> {
				input = ""
				onClose()
				return true
			}

			GLFW.GLFW_KEY_BACKSPACE -> {
				if (input.isEmpty()) return true
				input = input.substring(0, input.offsetByCodePoints(input.length, -1))
				SearchOverlay.suggestions(input, matches)
				return true
			}
		}
		return super.keyPressed(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val codepoint = event.codepoint()
		if (!isPrintable(codepoint)) return super.charTyped(event)
		if (input.length >= MAX_LENGTH) return true
		input += String(Character.toChars(codepoint))
		SearchOverlay.suggestions(input, matches)
		return true
	}

	override fun onClose() {
		if (!pushed) {
			pushed = true
			push()
		}
		super.onClose()
	}

	private fun push() {
		SearchOverlay.remember(place, input)
		val split = SearchOverlay.splitOverTwoLines(SearchOverlay.quoted(input))
		minecraft.connection?.send(
			ServerboundSignUpdatePacket(pos, frontText, split.first, split.second, lines[2], lines[3])
		)
	}

	private fun drawInput(graphics: GuiGraphicsExtractor, left: Int, top: Int) {
		val boxLeft = left + PAD
		val boxRight = boxLeft + ROW_WIDTH
		RoundedGui.pillFrame(graphics, boxLeft, top, boxRight, top + BOX_HEIGHT, GlassGui.interactive(), DhenPalette.accent)
		val shown = inputMemo.fit(font, input.ifEmpty { PLACEHOLDER }, ROW_WIDTH - PAD * 2, fromEnd = true)
		val ink = if (input.isEmpty()) DhenPalette.TEXT_DISABLED else DhenPalette.TEXT_PRIMARY
		val textLeft = boxLeft + PAD
		inputMemo.text(graphics, font, shown, textLeft, textTop(font, top, BOX_HEIGHT), ink)
		if (input.isEmpty()) return
		val caret = textLeft + inputMemo.width(font, shown)
		SharpGui.fill(graphics, caret, top + CARET_INSET, caret + 1, top + BOX_HEIGHT - CARET_INSET, DhenPalette.accent)
	}

	private fun drawRow(
		graphics: GuiGraphicsExtractor,
		memo: TextMemo,
		label: String,
		id: String,
		left: Int,
		top: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val rowLeft = left + PAD
		val rowRight = rowLeft + ROW_WIDTH
		val hovered = mouseX in rowLeft until rowRight && mouseY in top until top + ROW
		RoundedGui.pill(graphics, rowLeft, top, rowRight, top + ROW - ROW_GAP, GlassGui.raised(hovered))
		val icon = if (id.isEmpty()) null else ItemRepo.stack(id)
		val textLeft = if (icon == null) rowLeft + PAD else rowLeft + ICON_INSET * 2 + ICON
		if (icon != null) ItemGui.stack(graphics, icon, rowLeft + ICON_INSET, top + ICON_INSET)
		val shown = memo.fit(font, label, rowRight - textLeft - PAD)
		memo.text(graphics, font, shown, textLeft, textTop(font, top, ROW - ROW_GAP), DhenPalette.TEXT_PRIMARY)
	}

	private fun panelTop(): Int = (height - panelHeight()) / 2

	private fun panelHeight(): Int {
		val rows = 2 + matches.size + history.size
		return rows * ROW + (if (history.isEmpty()) 0 else RECENT_HEIGHT) + PAD
	}

	private companion object {
		private const val TITLE = "Search"
		private const val PLACEHOLDER = "Type an item name"
		private const val RECENT_LABEL = "Recent"
		private const val MAX_LENGTH = 30
		private const val MAX_ROWS = 20
		private const val PANEL_HALF_WIDTH = 116
		private const val PANEL_RADIUS = 6f
		private const val ROW_WIDTH = 216
		private const val ROW = 20
		private const val ROW_GAP = 2
		private const val RECENT_HEIGHT = 14
		private const val BOX_HEIGHT = 18
		private const val PAD = 8
		private const val ICON = 16
		private const val ICON_INSET = 2
		private const val CARET_INSET = 4
	}
}
