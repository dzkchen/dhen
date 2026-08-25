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
	private var heldPos: BlockPos? = null

	private var heldOldState: BlockState? = null

	private var heldNewState: BlockState? = null

	var pos: BlockPos
		get() = heldPos!!
		internal set(value) {
			heldPos = value
		}

	var oldState: BlockState
		get() = heldOldState!!
		internal set(value) {
			heldOldState = value
		}

	var newState: BlockState
		get() = heldNewState!!
		internal set(value) {
			heldNewState = value
		}

	fun retainedPos(): BlockPos = pos.immutable()

	internal fun forget() {
		heldPos = null
		heldOldState = null
		heldNewState = null
	}
}

class EntityUnloadEvent internal constructor(val entity: Entity) : Event
