package io.github.dzkchen.dhen.event

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class ContainerReadyEvent internal constructor(
	val title: Component,
	val windowId: Int,
	val slotCount: Int,
	val stacks: List<ItemStack>
) : Event

class ContainerUpdatedEvent internal constructor(
	val title: Component,
	val windowId: Int,
	val slotCount: Int,
	val stacks: List<ItemStack>
) : Event

class ContainerClosedEvent internal constructor(
	val title: Component,
	val windowId: Int,
	val reopening: Boolean
) : Event
