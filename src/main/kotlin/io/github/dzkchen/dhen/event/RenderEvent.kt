package io.github.dzkchen.dhen.event

import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.util.ARGB
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.Entity

class EntityGlowEvent internal constructor() : DeepProfiledEvent {
	lateinit var entity: Entity
		internal set

	var glowing: Boolean = false

	var color: Int = 0

	internal fun seed(vanillaOutline: Int) {
		glowing = vanillaOutline != EntityRenderState.NO_OUTLINE
		color = if (glowing) vanillaOutline else UNTEAMED_GLOW
	}

	internal fun outline(): Int =
		if (glowing) ARGB.opaque(color) else EntityRenderState.NO_OUTLINE

	private companion object {
		private val UNTEAMED_GLOW = ARGB.white(0xFF)
	}
}

class EntityRenderEvent internal constructor() : DeepProfiledEvent, Cancellable {
	lateinit var entity: Entity
		internal set

	override var cancelled: Boolean = false
}

class BossBarUpdateEvent internal constructor() : DeepProfiledEvent, Cancellable {
	lateinit var bossBar: BossEvent
		internal set

	override var cancelled: Boolean = false
}
