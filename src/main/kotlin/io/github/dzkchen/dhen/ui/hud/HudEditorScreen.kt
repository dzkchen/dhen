package io.github.dzkchen.dhen.ui.hud

import io.github.dzkchen.dhen.gui.LiveWorldScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

internal class HudEditorScreen(
	private val canvas: HudCanvas,
	private val opened: () -> Unit = {},
	private val closed: () -> Unit = {}
) : LiveWorldScreen(Component.literal("Dhen HUD Editor")) {
	override fun init() = opened()

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) = Unit

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		canvas.paint(graphics, font, width, height, mouseX, mouseY, HudCanvas.HINTS)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val x = event.x().toInt()
		val y = event.y().toInt()
		when (event.button()) {
			GLFW.GLFW_MOUSE_BUTTON_LEFT -> canvas.press(x, y)
			GLFW.GLFW_MOUSE_BUTTON_RIGHT -> canvas.reset(x, y)
			else -> return super.mouseClicked(event, doubleClick)
		}
		return true
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		if (!canvas.dragging) return super.mouseDragged(event, dragX, dragY)
		canvas.drag(event.x().toInt(), event.y().toInt(), snap = !event.hasAltDown())
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		if (!canvas.dragging || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(event)
		canvas.release()
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		canvas.rescale(mouseX.toInt(), mouseY.toInt(), scrollY)
		return true
	}

	override fun keyPressed(event: KeyEvent): Boolean =
		canvas.keyed(event.key()) || super.keyPressed(event)

	override fun removed() {
		canvas.release()
		closed()
		super.removed()
	}
}

internal fun editingHud(): Boolean =
	Minecraft.getInstance().gui.screen() is HudEditorScreen || MenuHudEditor.editing
