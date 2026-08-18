package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot

internal object ScreenHooks {
	private val failsafe = Failsafe("Dhen {} failed, its screen events are off until restart")

	@Volatile
	private var channels: Channels? = null

	fun install(bus: EventBus) {
		channels = Channels(bus)
	}

	fun uninstall() {
		channels = null
	}

	fun active(): Boolean = channels != null

	@JvmStatic
	fun screenChanged(closing: Screen?, opening: Screen?) {
		guarded("screen change") { channels ->
			channels.changed(closing, opening)
			false
		}
	}

	@JvmStatic
	fun beforeScreenRender(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean =
		guarded("screen render") { it.rendering(screen, graphics, mouseX, mouseY) }

	@JvmStatic
	fun afterScreenRender(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		guarded("screen rendered") { channels ->
			channels.rendered(screen, graphics, mouseX, mouseY)
			false
		}
	}

	@JvmStatic
	fun beforeContainerClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent, hovered: Slot?): Boolean =
		guarded("container click") { it.clicked(screen, click, hovered) }

	@JvmStatic
	fun beforeContainerKey(screen: AbstractContainerScreen<*>, key: KeyEvent, hovered: Slot?): Boolean =
		guarded("container key") { it.pressed(screen, key, hovered) }

	private inline fun guarded(label: String, block: (Channels) -> Boolean): Boolean {
		val channels = channels ?: return false
		return try {
			block(channels)
		} catch (throwable: Throwable) {
			this.channels = null
			failsafe.fail(label, throwable)
			false
		}
	}

	private class Channels(bus: EventBus) {
		private val opens = bus.type<GuiOpenEvent>()
		private val closes = bus.type<GuiCloseEvent>()
		private val clicks = bus.type<ContainerClickEvent>()
		private val keys = bus.type<ContainerKeyEvent>()
		private val renderPre = bus.type<ScreenRenderEvent.Pre>()
		private val renderPost = bus.type<ScreenRenderEvent.Post>()
		private val preEvents = ReusableEvent(ScreenRenderEvent::Pre)
		private val postEvents = ReusableEvent(ScreenRenderEvent::Post)

		fun changed(closing: Screen?, opening: Screen?) {
			if (closing != null) closes.dispatch(GuiCloseEvent(closing))
			if (opening != null) opens.dispatch(GuiOpenEvent(opening))
		}

		fun rendering(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
			val event = preEvents.borrow()
			event.cancelled = false
			event.screen = screen
			event.graphics = graphics
			event.mouseX = mouseX
			event.mouseY = mouseY
			try {
				renderPre.dispatch(event)
			} finally {
				preEvents.release(event)
			}
			return event.cancelled
		}

		fun rendered(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
			val event = postEvents.borrow()
			event.screen = screen
			event.graphics = graphics
			event.mouseX = mouseX
			event.mouseY = mouseY
			try {
				renderPost.dispatch(event)
			} finally {
				postEvents.release(event)
			}
		}

		fun clicked(screen: AbstractContainerScreen<*>, click: MouseButtonEvent, hovered: Slot?): Boolean {
			val event = ContainerClickEvent(screen, click, hovered)
			clicks.dispatch(event)
			return event.cancelled
		}

		fun pressed(screen: AbstractContainerScreen<*>, key: KeyEvent, hovered: Slot?): Boolean {
			val event = ContainerKeyEvent(screen, key, hovered)
			keys.dispatch(event)
			return event.cancelled
		}
	}
}
