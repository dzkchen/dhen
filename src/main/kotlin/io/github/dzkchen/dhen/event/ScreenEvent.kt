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
	lateinit var screen: AbstractContainerScreen<*>
		internal set

	lateinit var graphics: GuiGraphicsExtractor
		internal set

	lateinit var slot: Slot
		internal set

	class Pre internal constructor() : SlotRenderEvent(), Cancellable {
		override var cancelled: Boolean = false
	}

	class Post internal constructor() : SlotRenderEvent()
}

class TooltipEvent internal constructor() : DeepProfiledEvent {
	lateinit var screen: AbstractContainerScreen<*>
		internal set

	lateinit var graphics: GuiGraphicsExtractor
		internal set

	lateinit var hoveredSlot: Slot
		internal set

	lateinit var stack: ItemStack
		internal set

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
