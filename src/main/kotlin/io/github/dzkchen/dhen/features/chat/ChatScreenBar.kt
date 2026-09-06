package io.github.dzkchen.dhen.features.chat

import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.RoundedGui
import io.github.dzkchen.dhen.gui.centeredText
import io.github.dzkchen.dhen.gui.textTop
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

object ChatScreenBar {
	const val IGNORED = 0
	const val HANDLED = 1
	const val OPENED = 2
	const val CLOSED = 3
	const val TO_INPUT = 4

	private val tabMemos = Array(ChatTab.entries.size) { DhenType.memo() }
	private val countMemo = DhenType.memo()

	@JvmStatic
	fun searchBox(font: Font, width: Int, height: Int): EditBox? {
		if (!ChatSearch.enabled) return null
		if (ChatSearch.pinned) ChatSearch.show()
		if (!ChatSearch.open) return null
		val top = searchTop(height)
		val box = EditBox(font, INSET + PAD, top, width - 2 * (INSET + PAD), BAR, DhenType.component(HINT))
		box.setMaxLength(QUERY_LIMIT)
		box.setBordered(false)
		box.setCanLoseFocus(true)
		box.setHint(DhenType.component(HINT))
		box.value = ChatSearch.query
		box.setResponder(ChatSearch::ask)
		return box
	}

	@JvmStatic
	fun closed() {
		ChatSearch.close()
	}

	@JvmStatic
	fun keyed(key: KeyEvent, box: EditBox?, suggesting: Boolean): Int {
		if (!ChatSearch.enabled) return IGNORED
		if (key.key() == GLFW.GLFW_KEY_F && key.hasControlDown()) return toggled(box)
		if (!ChatSearch.open || box == null) return IGNORED
		if (key.key() == GLFW.GLFW_KEY_ESCAPE) return if (suggesting) IGNORED else escaped(box)
		if (!box.isFocused) return IGNORED
		if (key.key() != GLFW.GLFW_KEY_ENTER && key.key() != GLFW.GLFW_KEY_KP_ENTER) return IGNORED
		return TO_INPUT
	}

	@JvmStatic
	fun clicked(click: MouseButtonEvent, height: Int, font: Font): Boolean {
		if (!ChatTabs.shown) return false
		val tab = tabAt(click.x().toInt(), click.y().toInt(), height, font) ?: return false
		ChatTabs.select(tab)
		return true
	}

	@JvmStatic
	fun focus(screen: Screen, box: EditBox?, input: EditBox, onSearch: Boolean) {
		input.setCanLoseFocus(box != null)
		if (onSearch && box != null) {
			input.isFocused = false
			screen.focused = box
			box.isFocused = true
			return
		}
		box?.isFocused = false
		screen.focused = input
		input.isFocused = true
	}

	@JvmStatic
	fun beneath(graphics: GuiGraphicsExtractor, font: Font, width: Int, height: Int, mouseX: Int, mouseY: Int) {
		if (ChatSearch.enabled && ChatSearch.open) {
			val top = searchTop(height)
			GlassGui.roundedFrame(
				graphics,
				INSET,
				top,
				width - INSET,
				top + BAR,
				RADIUS,
				GlassGui.canvas(),
				DhenPalette.BORDER
			)
		}
		if (!ChatTabs.shown) return
		val top = tabsTop(height)
		var left = INSET
		for (tab in ChatTab.entries) {
			val right = left + tabWidth(font, tab)
			val active = ChatTabs.active == tab
			val hovered = mouseX in left until right && mouseY in top until top + BAR
			RoundedGui.pill(graphics, left, top, right, top + BAR, if (active) DhenPalette.accent else GlassGui.raised(hovered))
			centeredText(
				graphics,
				font,
				tabMemos[tab.ordinal],
				tab.label,
				left,
				right,
				textTop(font, top, BAR),
				if (active) DhenPalette.TEXT_ON_ACCENT else DhenPalette.label(hovered),
				PAD
			)
			left = right + GAP
		}
	}

	@JvmStatic
	fun above(graphics: GuiGraphicsExtractor, font: Font, width: Int, height: Int) {
		if (!ChatSearch.filtering) return
		val label = ChatSearch.countLabel()
		countMemo.text(
			graphics,
			font,
			label,
			width - INSET - PAD - countMemo.width(font, label),
			textTop(font, searchTop(height), BAR),
			DhenPalette.TEXT_SECONDARY
		)
	}

	private fun toggled(box: EditBox?): Int {
		if (!ChatSearch.open) {
			ChatSearch.show()
			return OPENED
		}
		if (!ChatSearch.pinned) {
			ChatSearch.close()
			return CLOSED
		}
		return if (box != null && box.isFocused) TO_INPUT else OPENED
	}

	private fun escaped(box: EditBox): Int {
		if (box.value.isNotEmpty()) {
			box.value = ""
			return HANDLED
		}
		if (ChatSearch.pinned) return IGNORED
		ChatSearch.close()
		return CLOSED
	}

	private fun tabAt(x: Int, y: Int, height: Int, font: Font): ChatTab? {
		val top = tabsTop(height)
		if (y < top || y >= top + BAR) return null
		var left = INSET
		for (tab in ChatTab.entries) {
			val right = left + tabWidth(font, tab)
			if (x in left until right) return tab
			left = right + GAP
		}
		return null
	}

	private fun tabWidth(font: Font, tab: ChatTab): Int =
		maxOf(BAR, tabMemos[tab.ordinal].width(font, tab.label) + 2 * PAD)

	private fun searchTop(height: Int): Int = height - INPUT_BAND - BAR

	private fun tabsTop(height: Int): Int =
		if (ChatSearch.enabled && ChatSearch.open) searchTop(height) - GAP - BAR else searchTop(height)

	private const val INPUT_BAND = 14
	private const val BAR = 12
	private const val GAP = 2
	private const val INSET = 2
	private const val PAD = 3
	private const val RADIUS = 3f
	private const val QUERY_LIMIT = 128
	private const val HINT = "Search chat"
}
