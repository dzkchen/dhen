package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.util.Failsafe
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

internal object ScreenHooks : Hooks {
	override val feed = "Screens"

	private val failsafe = Failsafe("Dhen {} failed, its screen events are off until restart")

	private var channels: Channels? = null

	private var disconnects: Handle? = null

	private var changing = false

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
		disconnects = bus.subscribe<WorldChangeEvent> { change ->
			if (change.phase == WorldChange.DISCONNECT) guarded("screen world change") { it.forget(); false }
		}
	}

	override fun uninstall() {
		disconnects?.unsubscribe()
		disconnects = null
		changing = false
		channels = null
	}

	override fun active(): Boolean = channels != null

	@JvmStatic
	fun screenChanged(closing: Screen?, opening: Screen?) {
		if (changing) return
		changing = true
		try {
			guarded("screen change") { channels ->
				channels.changed(closing, opening)
				false
			}
		} finally {
			changing = false
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

	@JvmStatic
	fun beforeContainerScroll(
		screen: AbstractContainerScreen<*>,
		mouseX: Double,
		mouseY: Double,
		scrollX: Double,
		scrollY: Double,
		hovered: Slot?
	): Boolean = guarded("container scroll") { it.scrolled(screen, mouseX, mouseY, scrollX, scrollY, hovered) }

	@JvmStatic
	fun beforeSlotRender(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, slot: Slot): Boolean =
		guarded("slot render") { it.slotRendering(screen, graphics, slot) }

	@JvmStatic
	fun afterSlotRender(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, slot: Slot) {
		guarded("slot rendered") { channels ->
			channels.slotRendered(screen, graphics, slot)
			false
		}
	}

	@JvmStatic
	fun beforeTooltip(
		screen: AbstractContainerScreen<*>,
		graphics: GuiGraphicsExtractor,
		hovered: Slot?,
		stack: ItemStack,
		lines: List<Component>,
		x: Int,
		y: Int
	): TooltipEvent? {
		if (hovered == null || stack.isEmpty || lines.isEmpty()) return null
		val channels = channels ?: return null
		return try {
			channels.tooltip(screen, graphics, hovered, stack, lines, x, y)
		} catch (throwable: Throwable) {
			latchOff("tooltip", throwable)
			null
		}
	}

	@JvmStatic
	fun releaseTooltip(event: TooltipEvent) {
		channels?.releaseTooltip(event)
	}

	private inline fun guarded(label: String, block: (Channels) -> Boolean): Boolean {
		val channels = channels ?: return false
		return try {
			block(channels)
		} catch (throwable: Throwable) {
			latchOff(label, throwable)
			false
		}
	}

	private fun latchOff(label: String, throwable: Throwable) {
		uninstall()
		failsafe.fail(label, throwable)
	}

	private class Channels(bus: EventBus) {
		private val opens = bus.type<GuiOpenEvent>()
		private val closes = bus.type<GuiCloseEvent>()
		private val clicks = bus.type<ContainerClickEvent>()
		private val keys = bus.type<ContainerKeyEvent>()
		private val scrolls = bus.type<ContainerScrollEvent>()
		private val renderPre = bus.type<ScreenRenderEvent.Pre>()
		private val renderPost = bus.type<ScreenRenderEvent.Post>()
		private val slotPre = bus.type<SlotRenderEvent.Pre>()
		private val slotPost = bus.type<SlotRenderEvent.Post>()
		private val tooltips = bus.type<TooltipEvent>()
		private val preEvents = ReusableEvent(ScreenRenderEvent::Pre)
		private val postEvents = ReusableEvent(ScreenRenderEvent::Post)
		private val slotPreEvents = ReusableEvent(SlotRenderEvent::Pre)
		private val slotPostEvents = ReusableEvent(SlotRenderEvent::Post)
		private val tooltipEvents = ReusableEvent(::TooltipEvent)

		fun forget() {
			slotPreEvents.forget { it.forget() }
			slotPostEvents.forget { it.forget() }
			tooltipEvents.forget { it.forget() }
		}

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
			return try {
				renderPre.dispatch(event)
				event.cancelled
			} finally {
				preEvents.release(event)
			}
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

		fun scrolled(
			screen: AbstractContainerScreen<*>,
			mouseX: Double,
			mouseY: Double,
			scrollX: Double,
			scrollY: Double,
			hovered: Slot?
		): Boolean {
			val event = ContainerScrollEvent(screen, mouseX, mouseY, scrollX, scrollY, hovered)
			scrolls.dispatch(event)
			return event.cancelled
		}

		fun slotRendering(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, slot: Slot): Boolean {
			val event = slotPreEvents.borrow()
			event.cancelled = false
			event.screen = screen
			event.graphics = graphics
			event.slot = slot
			return try {
				slotPre.dispatch(event)
				event.cancelled
			} finally {
				slotPreEvents.release(event)
			}
		}

		fun slotRendered(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, slot: Slot) {
			val event = slotPostEvents.borrow()
			event.screen = screen
			event.graphics = graphics
			event.slot = slot
			try {
				slotPost.dispatch(event)
			} finally {
				slotPostEvents.release(event)
			}
		}

		fun tooltip(
			screen: AbstractContainerScreen<*>,
			graphics: GuiGraphicsExtractor,
			hovered: Slot,
			stack: ItemStack,
			lines: List<Component>,
			x: Int,
			y: Int
		): TooltipEvent {
			val event = tooltipEvents.borrow()
			event.screen = screen
			event.graphics = graphics
			event.hoveredSlot = hovered
			event.stack = stack
			event.x = x
			event.y = y
			event.reuse(lines)
			tooltips.dispatch(event)
			return event
		}

		fun releaseTooltip(event: TooltipEvent) {
			tooltipEvents.release(event)
		}
	}
}
