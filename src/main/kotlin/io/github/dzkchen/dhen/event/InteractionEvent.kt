package io.github.dzkchen.dhen.event

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

sealed class InteractionEvent : Event, Cancellable {
	override var cancelled: Boolean = false

	class UseBlock internal constructor(
		val pos: BlockPos,
		val hand: InteractionHand,
		val item: ItemStack
	) : InteractionEvent()

	class UseEntity internal constructor(
		val entity: Entity,
		val hand: InteractionHand,
		val item: ItemStack
	) : InteractionEvent()

	class UseItem internal constructor(
		val hand: InteractionHand,
		val item: ItemStack
	) : InteractionEvent()

	class AttackBlock internal constructor(val pos: BlockPos) : InteractionEvent()

	class AttackEntity internal constructor(val entity: Entity) : InteractionEvent()

	class Attack internal constructor() : InteractionEvent()
}
