package io.github.dzkchen.dhen.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

internal class TamperWarningScreen(
	private val parent: Screen,
	expectedDigest: String?,
	actualDigest: String?,
	private val releaseUrl: String,
	private val dismiss: () -> Unit,
	private val openRelease: (String) -> Unit
) : Screen(Component.literal(TITLE)) {
	private val lines = arrayOf(
		"Dhen could not verify this installation.",
		"The running jar does not match the official release.",
		"It may have been modified or corrupted.",
		"Your account and data could be at risk.",
		"Download a clean copy from the official release page.",
		"Continue only if you understand the risk."
	)
	private val hashes = arrayOf(
		"Expected: ${shortDigest(expectedDigest)}",
		"Actual: ${shortDigest(actualDigest)}"
	)
	private val lineMemos = Array(lines.size) { DhenType.memo() }
	private val hashMemos = Array(hashes.size) { DhenType.memo() }
	private val titleMemo = DhenType.memo()
	private val downloadMemo = DhenType.memo()
	private val dismissMemo = DhenType.memo()

	override fun isPauseScreen(): Boolean = false

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val left = panelLeft()
		val top = panelTop()
		val right = left + panelWidth()
		GlassGui.roundedFrame(graphics, left, top, right, top + PANEL_HEIGHT, PANEL_RADIUS, GlassGui.canvas(), DhenPalette.BORDER)
		drawCentered(graphics, titleMemo, TITLE, left, right, top + TITLE_TOP, DhenPalette.accent)
		var y = top + LINES_TOP
		for (index in lines.indices) {
			drawCentered(graphics, lineMemos[index], lines[index], left, right, y, DhenPalette.TEXT_PRIMARY)
			y += LINE_HEIGHT
		}
		y += HASH_GAP
		for (index in hashes.indices) {
			drawCentered(graphics, hashMemos[index], hashes[index], left, right, y, DhenPalette.TEXT_SECONDARY)
			y += LINE_HEIGHT
		}
		val downloadTop = top + DOWNLOAD_TOP
		val dismissTop = top + DISMISS_TOP
		drawButton(graphics, downloadMemo, DOWNLOAD_LABEL, left, right, downloadTop, mouseX, mouseY)
		drawButton(graphics, dismissMemo, DISMISS_LABEL, left, right, dismissTop, mouseX, mouseY)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
		val mouseX = event.x().toInt()
		val mouseY = event.y().toInt()
		val left = buttonLeft()
		val right = left + BUTTON_WIDTH
		val top = panelTop()
		if (mouseX !in left until right) return super.mouseClicked(event, doubleClick)
		when (mouseY) {
			in top + DOWNLOAD_TOP until top + DOWNLOAD_TOP + BUTTON_HEIGHT -> {
				openRelease(releaseUrl)
				onClose()
				return true
			}
			in top + DISMISS_TOP until top + DISMISS_TOP + BUTTON_HEIGHT -> {
				dismiss()
				onClose()
				return true
			}
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun onClose() {
		minecraft.gui.setScreen(parent)
	}

	private fun drawButton(
		graphics: GuiGraphicsExtractor,
		memo: TextMemo,
		label: String,
		panelLeft: Int,
		panelRight: Int,
		top: Int,
		mouseX: Int,
		mouseY: Int
	) {
		val left = panelLeft + (panelRight - panelLeft - BUTTON_WIDTH) / 2
		val hovered = mouseX in left until left + BUTTON_WIDTH && mouseY in top until top + BUTTON_HEIGHT
		RoundedGui.pill(graphics, left, top, left + BUTTON_WIDTH, top + BUTTON_HEIGHT, GlassGui.raised(hovered))
		drawCentered(graphics, memo, label, left, left + BUTTON_WIDTH, textTop(top, BUTTON_HEIGHT), DhenPalette.TEXT_PRIMARY)
	}

	private fun drawCentered(
		graphics: GuiGraphicsExtractor,
		memo: TextMemo,
		text: String,
		left: Int,
		right: Int,
		top: Int,
		color: Int
	) {
		val shown = memo.fit(font, text, right - left - 2 * TEXT_PAD)
		memo.text(graphics, font, shown, left + (right - left - memo.width(font, shown)) / 2, top, color)
	}

	private fun textTop(top: Int, height: Int): Int = top + (height - DhenType.lineHeight(font)) / 2

	private fun panelWidth(): Int = minOf(PANEL_WIDTH, width - 2 * SCREEN_PAD)

	private fun panelLeft(): Int = (width - panelWidth()) / 2

	private fun panelTop(): Int = (height - PANEL_HEIGHT) / 2

	private fun buttonLeft(): Int = (width - BUTTON_WIDTH) / 2

	private companion object {
		const val TITLE = "Jar integrity warning"
		const val DOWNLOAD_LABEL = "Download official release"
		const val DISMISS_LABEL = "I know what I'm doing"
		const val PANEL_WIDTH = 480
		const val PANEL_HEIGHT = 250
		const val PANEL_RADIUS = 6f
		const val SCREEN_PAD = 12
		const val TEXT_PAD = 12
		const val TITLE_TOP = 15
		const val LINES_TOP = 42
		const val LINE_HEIGHT = 14
		const val HASH_GAP = 7
		const val DOWNLOAD_TOP = 187
		const val DISMISS_TOP = 217
		const val BUTTON_WIDTH = 200
		const val BUTTON_HEIGHT = 20

		fun shortDigest(digest: String?): String = digest?.take(DIGEST_PREVIEW)?.plus(ELLIPSIS) ?: UNKNOWN

		private const val DIGEST_PREVIEW = 16
		private const val UNKNOWN = "unknown"
	}
}
