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

internal object ScreenHooks : GuardedHooks<ScreenHooks.Channels> {
	override val feed = "Screens"

	override val failsafe = Failsafe("Dhen {} failed, its screen events are off until restart")

	private var channels: Channels? = null

	private var changing = false

	fun install(bus: EventBus) {
		uninstall()
		channels = Channels(bus)
	}

	override fun uninstall() {
		changing = false
		channels = null
	}

	override fun bound() = channels

	@JvmStatic
	fun screenChanged(closing: Screen?, opening: Screen?): Screen? =
		guardedChange("screen change", opening) { it.changed(closing, opening) }

	@JvmStatic
	fun screenSynthesised(synthesised: Screen?): Screen? =
		guardedChange("screen synthesised", synthesised) { it.changed(null, synthesised) }

	@JvmStatic
	fun beforeScreenRender(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean =
		guarded("screen render", false) { it.rendering(screen, graphics, mouseX, mouseY) }

	@JvmStatic
	fun afterScreenRender(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		guarded("screen rendered") { it.rendered(screen, graphics, mouseX, mouseY) }
	}

	@JvmStatic
	fun beforeContainerClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent, hovered: Slot?): Boolean =
		guarded("container click", false) { it.clicked(screen, click, hovered) }

	@JvmStatic
	fun beforeContainerKey(screen: AbstractContainerScreen<*>, key: KeyEvent, hovered: Slot?): Boolean =
		guarded("container key", false) { it.pressed(screen, key, hovered) }

	@JvmStatic
	fun beforeContainerChar(screen: AbstractContainerScreen<*>, codepoint: Int): Boolean =
		guarded("container char", false) { it.typed(screen, codepoint) }

	@JvmStatic
	fun beforeContainerScroll(
		screen: AbstractContainerScreen<*>,
		mouseX: Double,
		mouseY: Double,
		scrollX: Double,
		scrollY: Double,
		hovered: Slot?
	): Boolean = guarded("container scroll", false) { it.scrolled(screen, mouseX, mouseY, scrollX, scrollY, hovered) }

	@JvmStatic
	fun beforeSlotRender(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, slot: Slot): Boolean =
		guarded("slot render", false) { it.slotRendering(screen, graphics, slot) }

	@JvmStatic
	fun afterSlotRender(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, slot: Slot) {
		guarded("slot rendered") { it.slotRendered(screen, graphics, slot) }
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
		return guarded("tooltip", null) { it.tooltip(screen, graphics, hovered, stack, lines, x, y) }
	}

	@JvmStatic
	fun releaseTooltip(event: TooltipEvent) {
		channels?.releaseTooltip(event)
	}

	private inline fun guardedChange(label: String, fallback: Screen?, block: (Channels) -> Screen?): Screen? {
		if (changing) return fallback
		changing = true
		return try {
			guarded(label, fallback, block)
		} finally {
			changing = false
		}
	}

	internal class Channels(bus: EventBus) {
		private val opens = bus.type<GuiOpenEvent>()
		private val closes = bus.type<GuiCloseEvent>()
		private val clicks = bus.type<ContainerClickEvent>()
		private val keys = bus.type<ContainerKeyEvent>()
		private val chars = bus.type<ContainerCharEvent>()
		private val scrolls = bus.type<ContainerScrollEvent>()
		private val renderPre = bus.type<ScreenRenderEvent.Pre>()
		private val renderPost = bus.type<ScreenRenderEvent.Post>()
		private val slotPre = bus.type<SlotRenderEvent.Pre>()
		private val slotPost = bus.type<SlotRenderEvent.Post>()
		private val tooltips = bus.type<TooltipEvent>()
		private val preEvents = ReusableEvent(ScreenRenderEvent::Pre, ScreenRenderEvent::forget)
		private val postEvents = ReusableEvent(ScreenRenderEvent::Post, ScreenRenderEvent::forget)
		private val slotPreEvents = ReusableEvent(SlotRenderEvent::Pre, SlotRenderEvent::forget)
		private val slotPostEvents = ReusableEvent(SlotRenderEvent::Post, SlotRenderEvent::forget)
		private val tooltipEvents = ReusableEvent(::TooltipEvent, TooltipEvent::forget)

		fun changed(closing: Screen?, opening: Screen?): Screen? {
			if (closing != null) closes.dispatch(GuiCloseEvent(closing))
			if (opening == null) return null
			val event = GuiOpenEvent(opening)
			opens.dispatch(event)
			return event.screen
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

		fun typed(screen: AbstractContainerScreen<*>, codepoint: Int): Boolean {
			val event = ContainerCharEvent(screen, codepoint)
			chars.dispatch(event)
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
