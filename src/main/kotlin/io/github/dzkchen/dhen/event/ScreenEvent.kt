package io.github.dzkchen.dhen.event

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class GuiOpenEvent internal constructor(val screen: Screen) : Event

class GuiCloseEvent internal constructor(val screen: Screen) : Event

sealed class ScreenRenderEvent : DeepProfiledEvent {
	lateinit var screen: Screen
		internal set

	lateinit var graphics: GuiGraphicsExtractor
		internal set

	var mouseX: Int = 0
		internal set

	var mouseY: Int = 0
		internal set

	class Pre internal constructor() : ScreenRenderEvent(), Cancellable {
		override var cancelled: Boolean = false
	}

	class Post internal constructor() : ScreenRenderEvent()
}

sealed class SlotRenderEvent : DeepProfiledEvent {
	private var host: AbstractContainerScreen<*>? = null

	private var canvas: GuiGraphicsExtractor? = null

	private var target: Slot? = null

	var screen: AbstractContainerScreen<*>
		get() = host!!
		internal set(value) {
			host = value
		}

	var graphics: GuiGraphicsExtractor
		get() = canvas!!
		internal set(value) {
			canvas = value
		}

	var slot: Slot
		get() = target!!
		internal set(value) {
			target = value
		}

	internal fun forget() {
		host = null
		canvas = null
		target = null
	}

	class Pre internal constructor() : SlotRenderEvent(), Cancellable {
		override var cancelled: Boolean = false
	}

	class Post internal constructor() : SlotRenderEvent()
}

class TooltipEvent internal constructor() : DeepProfiledEvent {
	private var host: AbstractContainerScreen<*>? = null

	private var canvas: GuiGraphicsExtractor? = null

	private var target: Slot? = null

	private var held: ItemStack? = null

	var screen: AbstractContainerScreen<*>
		get() = host!!
		internal set(value) {
			host = value
		}

	var graphics: GuiGraphicsExtractor
		get() = canvas!!
		internal set(value) {
			canvas = value
		}

	var hoveredSlot: Slot
		get() = target!!
		internal set(value) {
			target = value
		}

	var stack: ItemStack
		get() = held!!
		internal set(value) {
			held = value
		}

	var x: Int = 0

	var y: Int = 0

	private var vanillaLines: List<Component> = emptyList()

	private var rewrittenLines: MutableList<Component>? = null

	val lines: List<Component>
		get() = rewrittenLines ?: vanillaLines

	fun edit(): MutableList<Component> =
		rewrittenLines ?: ArrayList(vanillaLines).also { rewrittenLines = it }

	internal fun reuse(lines: List<Component>) {
		vanillaLines = lines
		rewrittenLines = null
	}

	internal fun forget() {
		host = null
		canvas = null
		target = null
		held = null
		reuse(emptyList())
	}
}

class ContainerClickEvent internal constructor(
	val screen: AbstractContainerScreen<*>,
	val click: MouseButtonEvent,
	val hoveredSlot: Slot?
) : Event, Cancellable {
	override var cancelled: Boolean = false
}

class ContainerKeyEvent internal constructor(
	val screen: AbstractContainerScreen<*>,
	val input: KeyEvent,
	val hoveredSlot: Slot?
) : Event, Cancellable {
	override var cancelled: Boolean = false
}

class ContainerScrollEvent internal constructor(
	val screen: AbstractContainerScreen<*>,
	val mouseX: Double,
	val mouseY: Double,
	val scrollX: Double,
	val scrollY: Double,
	val hoveredSlot: Slot?
) : Event, Cancellable {
	override var cancelled: Boolean = false
}
