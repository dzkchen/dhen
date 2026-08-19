package io.github.dzkchen.dhen.event

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult

sealed class InteractionEvent : Event {

	sealed class Pre : InteractionEvent(), Cancellable {
		override var cancelled: Boolean = false
	}

	class UseBlock internal constructor(
		val pos: BlockPos,
		val hand: InteractionHand,
		val item: ItemStack
	) : Pre()

	class UseEntity internal constructor(
		val entity: Entity,
		val hand: InteractionHand,
		val item: ItemStack
	) : Pre()

	class UseItem internal constructor(
		val hand: InteractionHand,
		val item: ItemStack
	) : Pre()

	class AttackBlock internal constructor(val pos: BlockPos) : Pre()

	class AttackEntity internal constructor(val entity: Entity) : Pre()

	class Attack internal constructor() : Pre()

	class UsedBlock internal constructor(
		val hand: InteractionHand,
		val hit: BlockHitResult,
		val result: InteractionResult
	) : InteractionEvent()
}
