package io.github.dzkchen.dhen.event

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState

enum class WorldChange {
	INIT,
	JOIN,
	DISCONNECT
}

class WorldChangeEvent internal constructor(val phase: WorldChange) : Event

class BlockChangeEvent internal constructor() : DeepProfiledEvent {
	lateinit var pos: BlockPos
		internal set

	lateinit var oldState: BlockState
		internal set

	lateinit var newState: BlockState
		internal set

	fun retainedPos(): BlockPos = pos.immutable()
}

class EntityUnloadEvent internal constructor(val entity: Entity) : Event
