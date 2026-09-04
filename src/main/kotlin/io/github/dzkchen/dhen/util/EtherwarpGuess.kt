package io.github.dzkchen.dhen.util

import io.github.dzkchen.dhen.data.item.SkyBlockItem
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.BigDripleafStemBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BubbleColumnBlock
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.ComparatorBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.DoublePlantBlock
import net.minecraft.world.level.block.DryVegetationBlock
import net.minecraft.world.level.block.FireBlock
import net.minecraft.world.level.block.FlowerBlock
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.GrowingPlantBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.LanternBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.MushroomBlock
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.NetherWartBlock
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.RedstoneTorchBlock
import net.minecraft.world.level.block.RepeaterBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.SeagrassBlock
import net.minecraft.world.level.block.ShortDryGrassBlock
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.SmallDripleafBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.StemBlock
import net.minecraft.world.level.block.SugarCaneBlock
import net.minecraft.world.level.block.TallFlowerBlock
import net.minecraft.world.level.block.TallGrassBlock
import net.minecraft.world.level.block.TallSeagrassBlock
import net.minecraft.world.level.block.TorchBlock
import net.minecraft.world.level.block.TripWireBlock
import net.minecraft.world.level.block.TripWireHookBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.WallSkullBlock
import net.minecraft.world.level.block.WebBlock
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sign

internal const val PASSABLE = 1

internal const val BLOCKS_FEET = 2

internal const val VOXEL_STEP_LIMIT = 1000

internal interface VoxelField {
	fun flagsAt(x: Int, y: Int, z: Int): Int

	fun collisionTopAt(x: Int, y: Int, z: Int): Double
}

class EtherwarpTarget internal constructor() {
	var found: Boolean = false
		internal set
	var succeeded: Boolean = false
		internal set
	var x: Int = 0
		internal set
	var y: Int = 0
		internal set
	var z: Int = 0
		internal set

	internal fun clear() {
		found = false
		succeeded = false
	}

	internal fun land(x: Int, y: Int, z: Int, succeeded: Boolean) {
		found = true
		this.succeeded = succeeded
		this.x = x
		this.y = y
		this.z = z
	}
}

internal fun standsOn(field: VoxelField, x: Int, y: Int, z: Int): Boolean {
	val flags = field.flagsAt(x, y, z)
	return flags and PASSABLE != 0 && flags and BLOCKS_FEET == 0
}

internal fun traverseVoxels(
	startX: Double,
	startY: Double,
	startZ: Double,
	endX: Double,
	endY: Double,
	endZ: Double,
	field: VoxelField,
	into: EtherwarpTarget
): EtherwarpTarget {
	into.clear()

	var x = floor(startX).toInt()
	var y = floor(startY).toInt()
	var z = floor(startZ).toInt()
	val lastX = floor(endX).toInt()
	val lastY = floor(endY).toInt()
	val lastZ = floor(endZ).toInt()

	val spanX = endX - startX
	val spanY = endY - startY
	val spanZ = endZ - startZ

	val stepX = sign(spanX).toInt()
	val stepY = sign(spanY).toInt()
	val stepZ = sign(spanZ).toInt()

	val inverseX = if (spanX != 0.0) 1.0 / spanX else Double.MAX_VALUE
	val inverseY = if (spanY != 0.0) 1.0 / spanY else Double.MAX_VALUE
	val inverseZ = if (spanZ != 0.0) 1.0 / spanZ else Double.MAX_VALUE

	val deltaX = abs(inverseX * stepX)
	val deltaY = abs(inverseY * stepY)
	val deltaZ = abs(inverseZ * stepZ)

	var crossX = abs((x + max(stepX, 0) - startX) * inverseX)
	var crossY = abs((y + max(stepY, 0) - startY) * inverseY)
	var crossZ = abs((z + max(stepZ, 0) - startZ) * inverseZ)

	var steps = 0
	while (steps < VOXEL_STEP_LIMIT) {
		if (field.flagsAt(x, y, z) and PASSABLE == 0) {
			val standY = y + max(1, ceil(field.collisionTopAt(x, y, z)).toInt())
			into.land(x, y, z, standsOn(field, x, standY, z) && standsOn(field, x, standY + 1, z))
			return into
		}
		if (x == lastX && y == lastY && z == lastZ) return into

		when {
			crossX <= crossY && crossX <= crossZ -> {
				crossX += deltaX
				x += stepX
			}

			crossY <= crossZ -> {
				crossY += deltaY
				y += stepY
			}

			else -> {
				crossZ += deltaZ
				z += stepZ
			}
		}
		steps++
	}
	return into
}

object EtherwarpGuess {
	const val ETHERWARP_CONDUIT = "ETHERWARP_CONDUIT"

	private const val BASE_DISTANCE = 57.0
	private const val STANDING_EYE = 1.62
	private const val CROUCHING_EYE = 1.27
	private const val SWIMMING_EYE = 0.4
	private const val DEGREES_TO_RADIANS = Math.PI.toFloat() / 180f

	private val cursor = BlockPos.MutableBlockPos()

	private val blockFlags: IntArray by lazy {
		val flags = IntArray(Block.BLOCK_STATE_REGISTRY.size())
		Block.BLOCK_STATE_REGISTRY.forEach { state -> flags[Block.getId(state)] = flagsOf(state) }
		flags
	}

	private val loadedWorld = object : VoxelField {
		override fun flagsAt(x: Int, y: Int, z: Int): Int {
			val world = Minecraft.getInstance().level ?: return PASSABLE
			val chunk = world.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
			return blockFlags[Block.getId(chunk.getBlockState(cursor.set(x, y, z)))]
		}

		override fun collisionTopAt(x: Int, y: Int, z: Int): Double {
			val world = Minecraft.getInstance().level ?: return 0.0
			cursor.set(x, y, z)
			return world.getBlockState(cursor).getCollisionShape(world, cursor).max(Direction.Axis.Y)
		}
	}

	fun etherwarpItem(stack: ItemStack): SkyBlockItem? {
		if (stack.isEmpty) return null
		val item = SkyBlockItems.of(stack)
		return if (item.ethermerge || item.id == ETHERWARP_CONDUIT) item else null
	}

	fun distanceOf(item: SkyBlockItem): Double = BASE_DISTANCE + item.tunedTransmission

	fun requiresSneak(item: SkyBlockItem): Boolean = item.id != ETHERWARP_CONDUIT

	fun aimedAtTarget(previousTickOrigin: Boolean, distance: Double, into: EtherwarpTarget): EtherwarpTarget {
		into.clear()
		val player = Minecraft.getInstance().player ?: return into
		if (Minecraft.getInstance().level == null) return into

		val eye = when {
			player.pose == Pose.SWIMMING -> SWIMMING_EYE
			player.isCrouching -> CROUCHING_EYE
			else -> STANDING_EYE
		}
		val originX = if (previousTickOrigin) player.xOld else player.x
		val originY = (if (previousTickOrigin) player.yOld else player.y) + eye
		val originZ = if (previousTickOrigin) player.zOld else player.z

		val pitch = (player.xRot * DEGREES_TO_RADIANS).toDouble()
		val yaw = (-player.yRot * DEGREES_TO_RADIANS).toDouble()
		val horizontal = Mth.cos(pitch)
		val lookX = (Mth.sin(yaw) * horizontal).toDouble()
		val lookY = -Mth.sin(pitch).toDouble()
		val lookZ = (Mth.cos(yaw) * horizontal).toDouble()
		return traverseVoxels(
			originX,
			originY,
			originZ,
			originX + lookX * distance,
			originY + lookY * distance,
			originZ + lookZ * distance,
			loadedWorld,
			into
		)
	}

	fun shapeAt(x: Int, y: Int, z: Int): VoxelShape {
		val world = Minecraft.getInstance().level ?: return Shapes.block()
		cursor.set(x, y, z)
		val shape = world.getBlockState(cursor).getShape(world, cursor)
		return if (shape.isEmpty) Shapes.block() else shape
	}

	private fun flagsOf(state: BlockState): Int {
		var flags = if (passable(state)) PASSABLE else 0
		if (blocksFeet(state)) flags = flags or BLOCKS_FEET
		return flags
	}

	private fun blocksFeet(state: BlockState): Boolean = when (state.block) {
		is SkullBlock, is WallSkullBlock -> true
		is FlowerPotBlock -> true
		is LadderBlock -> true
		is VineBlock -> true
		else -> false
	}

	private fun passable(state: BlockState): Boolean = when (state.block) {
		is AirBlock -> true
		is FlowerBlock, is TallGrassBlock, is BushBlock, is TallFlowerBlock, is ShortDryGrassBlock -> true
		is TorchBlock, is RedstoneTorchBlock -> true
		is TripWireBlock, is TripWireHookBlock -> true
		is RailBlock -> true
		is FireBlock -> true
		is VineBlock -> true
		is LiquidBlock -> true
		is SaplingBlock -> true
		is CropBlock, is StemBlock -> true
		is SeagrassBlock, is TallSeagrassBlock -> true
		is SugarCaneBlock -> true
		is MushroomBlock -> true
		is NetherWartBlock -> true
		is RedStoneWireBlock, is ComparatorBlock, is RepeaterBlock -> true
		is SmallDripleafBlock, is BigDripleafStemBlock -> true
		is DoublePlantBlock -> true
		is LeverBlock -> true
		is SnowLayerBlock -> true
		is BubbleColumnBlock -> true
		is GrowingPlantBlock -> true
		is PistonHeadBlock -> true
		is DryVegetationBlock -> true
		is ButtonBlock -> true
		is LanternBlock -> true
		is SkullBlock, is WallSkullBlock -> true
		is LadderBlock -> true
		is FlowerPotBlock -> true
		is WebBlock -> true
		is NetherPortalBlock -> true
		else -> false
	}
}
