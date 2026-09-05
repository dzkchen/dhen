package io.github.dzkchen.dhen.ui.hud

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.event.ContainerClickEvent
import io.github.dzkchen.dhen.event.ContainerKeyEvent
import io.github.dzkchen.dhen.event.ContainerScrollEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.GuiCloseEvent
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.KeyInputEvent
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.event.ScreenRenderEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW

internal object MenuHudEditor {
	@Volatile
	var editing: Boolean = false
		private set

	private var canvas: HudCanvas? = null
	private var runtime: HudRuntime? = null
	private var handles = emptyList<Handle>()

	fun install(bus: EventBus, canvas: HudCanvas, runtime: HudRuntime) {
		uninstall()
		this.canvas = canvas
		this.runtime = runtime
		handles = listOf(
			bus.subscribe<ScreenRenderEvent.Post> { drawn(it) },
			bus.subscribe<KeyInputEvent> { toggled(it) },
			bus.subscribe<ContainerKeyEvent> { pressed(it) },
			bus.subscribe<ContainerClickEvent> { clicked(it) },
			bus.subscribe<ContainerScrollEvent> { scrolled(it) },
			bus.subscribe<MouseInputEvent> { released(it) },
			bus.subscribe<GuiCloseEvent> { if (it.screen is AbstractContainerScreen<*>) stop() }
		)
	}

	fun uninstall() {
		for (handle in handles) handle.unsubscribe()
		handles = emptyList()
		stop()
		canvas = null
		runtime = null
	}

	private fun stop() {
		if (!editing) return
		canvas?.release()
		editing = false
	}

	private fun drawn(event: ScreenRenderEvent.Post) {
		if (event.screen !is AbstractContainerScreen<*>) return
		val runtime = this.runtime ?: return
		val font = Minecraft.getInstance().font
		if (!editing) {
			runtime.renderOverMenu(event.graphics, font, everything = false)
			return
		}
		val canvas = this.canvas ?: return
		if (canvas.dragging) canvas.drag(event.mouseX, event.mouseY, snap = !altDown())
		runtime.renderOverMenu(event.graphics, font, everything = true)
		canvas.paint(
			event.graphics,
			font,
			event.graphics.guiWidth(),
			event.graphics.guiHeight(),
			event.mouseX,
			event.mouseY,
			HudCanvas.MENU_HINTS
		)
	}

	private fun toggled(event: KeyInputEvent) {
		if (event.key != TOGGLE_KEY || event.action != InputAction.PRESS) return
		if (Minecraft.getInstance().gui.screen() !is AbstractContainerScreen<*>) return
		if (editing) stop() else editing = canvas != null
	}

	private fun pressed(event: ContainerKeyEvent) {
		if (!editing) return
		val key = event.input.key()
		if (key == GLFW.GLFW_KEY_ESCAPE || key == TOGGLE_KEY) {
			if (key == GLFW.GLFW_KEY_ESCAPE) stop()
			event.cancelled = true
			return
		}
		if (canvas?.keyed(key) == true) event.cancelled = true
	}

	private fun clicked(event: ContainerClickEvent) {
		if (!editing) return
		val canvas = this.canvas ?: return
		val x = event.click.x().toInt()
		val y = event.click.y().toInt()
		when (event.click.button()) {
			GLFW.GLFW_MOUSE_BUTTON_LEFT -> canvas.press(x, y)
			GLFW.GLFW_MOUSE_BUTTON_RIGHT -> canvas.reset(x, y)
		}
		event.cancelled = true
	}

	private fun scrolled(event: ContainerScrollEvent) {
		if (!editing) return
		canvas?.rescale(event.mouseX.toInt(), event.mouseY.toInt(), event.scrollY)
		event.cancelled = true
	}

	private fun released(event: MouseInputEvent) {
		if (!editing || event.action != InputAction.RELEASE) return
		if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return
		canvas?.release()
	}

	private fun altDown(): Boolean {
		val window = Minecraft.getInstance().window
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) ||
			InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT)
	}

	private const val TOGGLE_KEY = GLFW.GLFW_KEY_F8
}
