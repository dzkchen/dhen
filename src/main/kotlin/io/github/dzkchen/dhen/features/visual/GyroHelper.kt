package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.ColorSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.WorldRenderEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.WorldDraw
import io.github.dzkchen.dhen.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object GyroHelper : Module(
	name = "Gyro Helper",
	category = Category.VISUAL,
	description = "Marks where the Gyrokinetic Wand will land and the range it pulls from."
) {
	internal val drawBoxSetting = BooleanSetting(
		"Draw Box",
		true,
		description = "Boxes the block in the middle of the wand's sucking range."
	)
	internal val drawRingSetting = BooleanSetting(
		"Draw Sucking Range",
		true,
		description = "Draws the sucking range of the wand."
	)
	internal val ringWidthSetting = NumberSetting(
		"Ring Width",
		2.0,
		1.0,
		10.0,
		1.0,
		description = "Thickness of the ring."
	).withDependency { drawRingSetting.value }
	internal val boxColorSetting = ColorSetting(
		"Box Color",
		Color.rgba(0, 134, 255, 76),
		allowAlpha = true,
		description = "Colour of the landing box."
	).withDependency { drawBoxSetting.value }
	internal val ringColorSetting = ColorSetting(
		"Ring Color",
		Color.rgba(0, 134, 255),
		allowAlpha = true,
		description = "Colour of the sucking-range ring."
	).withDependency { drawRingSetting.value }

	private var drawBox by drawBoxSetting
	private var drawRing by drawRingSetting
	private var ringWidth by ringWidthSetting
	private var boxColor by boxColorSetting
	private var ringColor by ringColorSetting

	private var landing: BlockPos? = null

	init {
		on<ClientTickEvent.End> { landing = landing() }
		on<WorldRenderEvent> { render(it) }
	}

	override fun onDisabled() {
		landing = null
	}

	private fun landing(): BlockPos? {
		if (!drawBox && !drawRing) return null
		if (boxColor.alpha + ringColor.alpha == 0) return null
		val client = Minecraft.getInstance()
		val player = client.player ?: return null
		val level = client.level ?: return null
		if (SkyBlockItems.of(player.mainHandItem).id != GYROKINETIC_WAND) return null
		val hit = player.pick(THROW_RANGE, WHOLE_TICK, false)
		if (hit.type != HitResult.Type.BLOCK) return null
		val pos = (hit as BlockHitResult).blockPos
		if (level.getBlockState(pos).isAir) return null
		val above = level.getBlockState(pos.above())
		if (!above.isAir && !above.`is`(BlockTags.WOOL_CARPETS)) return null
		return pos
	}

	private fun render(event: WorldRenderEvent) {
		val pos = landing ?: return
		if (drawBox) WorldDraw.drawBox(event, pos, boxColor.argb)
		if (drawRing) {
			WorldDraw.drawWireCircle(
				event,
				pos.x + BLOCK_CENTER,
				pos.y + RING_HEIGHT,
				pos.z + BLOCK_CENTER,
				RING_RADIUS,
				ringColor.argb,
				ringWidth.toFloat()
			)
		}
	}

	private const val GYROKINETIC_WAND = "GYROKINETIC_WAND"
	private const val THROW_RANGE = 25.0
	private const val WHOLE_TICK = 1f
	private const val RING_RADIUS = 10.0
	private const val RING_HEIGHT = 2.05
	private const val BLOCK_CENTER = 0.5
}
