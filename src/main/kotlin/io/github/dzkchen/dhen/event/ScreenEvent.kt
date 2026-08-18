package io.github.dzkchen.dhen.event

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot

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
