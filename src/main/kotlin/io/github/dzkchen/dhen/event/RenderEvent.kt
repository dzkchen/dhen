package io.github.dzkchen.dhen.event

import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.util.ARGB
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.Entity

class EntityGlowEvent internal constructor() : DeepProfiledEvent {
	private var target: Entity? = null

	var entity: Entity
		get() = target!!
		internal set(value) {
			target = value
		}

	var glowing: Boolean = false

	var color: Int = 0

	internal fun seed(vanillaOutline: Int) {
		glowing = vanillaOutline != EntityRenderState.NO_OUTLINE
		color = if (glowing) vanillaOutline else UNTEAMED_GLOW
	}

	internal fun outline(): Int =
		if (glowing) ARGB.opaque(color) else EntityRenderState.NO_OUTLINE

	internal fun forget() {
		target = null
	}

	private companion object {
		private val UNTEAMED_GLOW = ARGB.white(0xFF)
	}
}

class EntityRenderEvent internal constructor() : DeepProfiledEvent, Cancellable {
	private var target: Entity? = null

	var entity: Entity
		get() = target!!
		internal set(value) {
			target = value
		}

	override var cancelled: Boolean = false

	internal fun forget() {
		target = null
	}
}

class BossBarUpdateEvent internal constructor() : DeepProfiledEvent, Cancellable {
	private var held: BossEvent? = null

	var bossBar: BossEvent
		get() = held!!
		internal set(value) {
			held = value
		}

	override var cancelled: Boolean = false

	internal fun forget() {
		held = null
	}
}
