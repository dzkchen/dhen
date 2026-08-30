package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.privacy.ServerPacks
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

internal class ServerPackConsentScreen(
	private val consent: ServerPacks.Consent
) : LiveWorldScreen(DhenType.component(TITLE)) {
	private val titleMemo = DhenType.memo()
	private val statusMemo = DhenType.memo()
	private val detailMemo = DhenType.memo()
	private val keepMemo = DhenType.memo()
	private val loadMemo = DhenType.memo()
	private var interacted = false

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		val left = panelLeft()
		val top = panelTop()
		val right = left + panelWidth()
		GlassGui.roundedFrame(graphics, left, top, right, top + PANEL_HEIGHT, PANEL_RADIUS, GlassGui.canvas(), DhenPalette.BORDER)
		drawCentered(graphics, titleMemo, TITLE, left, right, top + TITLE_TOP, DhenPalette.accent)
		drawCentered(graphics, statusMemo, status(), left, right, top + STATUS_TOP, DhenPalette.TEXT_PRIMARY)
		drawCentered(graphics, detailMemo, DETAIL, left, right, top + DETAIL_TOP, DhenPalette.TEXT_SECONDARY)
		drawButton(graphics, keepMemo, KEEP_LABEL, left, right, top + KEEP_TOP, mouseX, mouseY)
		drawButton(graphics, loadMemo, LOAD_LABEL, left, right, top + LOAD_TOP, mouseX, mouseY)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
		val x = event.x().toInt()
		val y = event.y().toInt()
		val left = buttonLeft()
		val right = left + BUTTON_WIDTH
		val top = panelTop()
		if (x !in left until right) return super.mouseClicked(event, doubleClick)
		when (y) {
			in top + KEEP_TOP until top + KEEP_TOP + BUTTON_HEIGHT -> {
				onClose()
				return true
			}
			in top + LOAD_TOP until top + LOAD_TOP + BUTTON_HEIGHT -> {
				loadFullPack()
				return true
			}
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun onClose() {
		interacted = true
		super.onClose()
	}

	override fun removed() {
		super.removed()
		if (!interacted) ServerPacks.requeueConsent(consent)
	}

	private fun loadFullPack() {
		interacted = true
		ServerPacks.apply(consent.id)
		try {
			minecraft.reloadResourcePacks()
		} catch (exception: Exception) {
			log.warn("Could not reload the full server resource pack", exception)
		}
		super.onClose()
	}

	private fun status(): String = if (consent.required) REQUIRED else OPTIONAL

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

	companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

		fun tryShow(minecraft: Minecraft) {
			if (minecraft.level == null || minecraft.gui.screen() != null) return
			val consent = ServerPacks.takeConsent(Util.getMillis()) ?: return
			minecraft.setScreenAndShow(ServerPackConsentScreen(consent))
		}

		private const val TITLE = "Server resource pack"
		private const val REQUIRED = "This server requires a resource pack."
		private const val OPTIONAL = "This server offered an optional resource pack."
		private const val DETAIL = "Dhen is currently loading only its language files."
		private const val KEEP_LABEL = "Keep it stripped"
		private const val LOAD_LABEL = "Load it anyway"
		private const val PANEL_WIDTH = 400
		private const val PANEL_HEIGHT = 170
		private const val PANEL_RADIUS = 6f
		private const val SCREEN_PAD = 12
		private const val TEXT_PAD = 12
		private const val TITLE_TOP = 18
		private const val STATUS_TOP = 48
		private const val DETAIL_TOP = 67
		private const val KEEP_TOP = 101
		private const val LOAD_TOP = 130
		private const val BUTTON_WIDTH = 190
		private const val BUTTON_HEIGHT = 20
	}
}
