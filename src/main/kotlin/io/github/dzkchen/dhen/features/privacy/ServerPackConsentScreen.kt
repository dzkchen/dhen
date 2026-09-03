package io.github.dzkchen.dhen.features.privacy

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenPalette
import io.github.dzkchen.dhen.gui.DhenType
import io.github.dzkchen.dhen.gui.GlassGui
import io.github.dzkchen.dhen.gui.LiveWorldScreen
import io.github.dzkchen.dhen.gui.centeredText
import io.github.dzkchen.dhen.gui.pillButton
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
		centeredText(graphics, font, titleMemo, TITLE, left, right, top + TITLE_TOP, DhenPalette.accent, TEXT_PAD)
		centeredText(graphics, font, statusMemo, status(), left, right, top + STATUS_TOP, DhenPalette.TEXT_PRIMARY, TEXT_PAD)
		centeredText(graphics, font, detailMemo, DETAIL, left, right, top + DETAIL_TOP, DhenPalette.TEXT_SECONDARY, TEXT_PAD)
		pillButton(graphics, font, keepMemo, KEEP_LABEL, buttonLeft(), buttonLeft() + BUTTON_WIDTH, top + KEEP_TOP, BUTTON_HEIGHT, mouseX, mouseY, TEXT_PAD)
		pillButton(graphics, font, loadMemo, LOAD_LABEL, buttonLeft(), buttonLeft() + BUTTON_WIDTH, top + LOAD_TOP, BUTTON_HEIGHT, mouseX, mouseY, TEXT_PAD)
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
