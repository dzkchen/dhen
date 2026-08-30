package io.github.dzkchen.dhen.features.visual

import com.mojang.blaze3d.vertex.PoseStack
import io.github.dzkchen.dhen.config.ActionSetting
import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.item.SkyBlockItems
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import kotlin.math.exp
import kotlin.math.max

object Animations : Module(
	name = "Animations",
	category = Category.VISUAL,
	description = "Customizes first-person held-item position, size, rotation, and swing motion."
) {
	internal val itemScaleSetting = NumberSetting(
		"Item Scale",
		0.0,
		-1.5,
		1.5,
		0.05,
		"0 is normal size, -0.5 is half size, and 1 is double size."
	)
	private var itemScale by itemScaleSetting

	internal val xSetting = NumberSetting("X", 0.0, -2.0, 2.0, 0.01)
	private var x by xSetting

	internal val ySetting = NumberSetting("Y", 0.0, -2.0, 2.0, 0.01)
	private var y by ySetting

	internal val zSetting = NumberSetting("Z", 0.0, -2.0, 2.0, 0.01)
	private var z by zSetting

	internal val rotationXSetting = NumberSetting("Rotation X", 0.0, -50.0, 50.0)
	private var rotationX by rotationXSetting

	internal val rotationYSetting = NumberSetting("Rotation Y", 0.0, -50.0, 50.0)
	private var rotationY by rotationYSetting

	internal val rotationZSetting = NumberSetting("Rotation Z", 0.0, -50.0, 50.0)
	private var rotationZ by rotationZSetting

	internal val swingXSetting = NumberSetting("Swing X", 1.0, 0.0, 2.0, 0.01)
	private var swingX by swingXSetting

	internal val swingYSetting = NumberSetting("Swing Y", 1.0, 0.0, 2.0, 0.01)
	private var swingY by swingYSetting

	internal val swingZSetting = NumberSetting("Swing Z", 1.0, 0.0, 2.0, 0.01)
	private var swingZ by swingZSetting

	internal val disableHandMovementSetting = BooleanSetting(
		"Disable Hand Movement",
		description = "Stops held items from moving when you look around."
	)
	private var disableHandMovement by disableHandMovementSetting

	internal val disableEquipAnimationSetting = BooleanSetting(
		"Disable Equip Animation",
		description = "Stops the item-lowering animation when your held item changes."
	)
	private var disableEquipAnimation by disableEquipAnimationSetting

	internal val disableSwingAnimationSetting = BooleanSetting(
		"Disable Swing Animation",
		description = "Stops the held-item swing animation."
	)
	private var disableSwingAnimation by disableSwingAnimationSetting

	internal val terminatorOnlySetting = BooleanSetting(
		"Terminator Only",
		description = "Disables the swing animation only while holding a Terminator."
	).withDependency { disableSwingAnimationSetting.on }
	private var terminatorOnly by terminatorOnlySetting

	internal val swingSpeedSetting = NumberSetting("Swing Speed", 0.0, -2.0, 1.0, 0.05)
		.withDependency { !disableSwingAnimationSetting.on || terminatorOnlySetting.on }
	private var swingSpeed by swingSpeedSetting

	internal val ignoreHasteSetting = BooleanSetting(
		"Ignore Haste",
		description = "Keeps haste from changing held-item swing speed."
	).withDependency { !disableSwingAnimationSetting.on || terminatorOnlySetting.on }
	private var ignoreHaste by ignoreHasteSetting

	internal val resetSetting = ActionSetting("Reset", ::resetControls)
	private val reset by resetSetting

	private val xRotation = Quaternionf()
	private val yRotation = Quaternionf()
	private val zRotation = Quaternionf()
	private var cachedRotationX = Double.NaN
	private var cachedRotationY = Double.NaN
	private var cachedRotationZ = Double.NaN

	@JvmStatic
	fun applyHandOffset(poseStack: PoseStack, hand: InteractionHand, itemStack: ItemStack) {
		if (!enabled || itemStack.isEmpty) return
		val sign = if (hand == InteractionHand.MAIN_HAND) 1.0 else -1.0
		poseStack.translate(
			(xSetting.amount * sign).toFloat(),
			ySetting.amount.toFloat(),
			zSetting.amount.toFloat()
		)
	}

	@JvmStatic
	fun applyItemTransform(poseStack: PoseStack) {
		if (!enabled) return
		val xDegrees = rotationXSetting.amount
		val yDegrees = rotationYSetting.amount
		val zDegrees = rotationZSetting.amount
		if (xDegrees != 0.0) poseStack.mulPose(xRotation(xDegrees))
		if (yDegrees != 0.0) poseStack.mulPose(yRotation(yDegrees))
		if (zDegrees != 0.0) poseStack.mulPose(zRotation(zDegrees))
		val scale = (1.0 + itemScaleSetting.amount).toFloat()
		if (scale != 1.0f) poseStack.scale(scale, scale, scale)
	}

	@JvmStatic
	fun applySwingOffset(poseStack: PoseStack, xOffset: Float, yOffset: Float, zOffset: Float) {
		if (!enabled) {
			poseStack.translate(xOffset, yOffset, zOffset)
			return
		}
		poseStack.translate(
			xOffset * swingXSetting.amount.toFloat(),
			yOffset * swingYSetting.amount.toFloat(),
			zOffset * swingZSetting.amount.toFloat()
		)
	}

	@JvmStatic
	fun swingProgress(original: Float, itemStack: ItemStack): Float {
		if (!enabled || !disableSwingAnimationSetting.on) return original
		if (!terminatorOnlySetting.on || SkyBlockItems.of(itemStack).id == TERMINATOR) return 1.0f
		return original
	}

	@JvmStatic
	fun disableEquipAnimation(): Boolean = enabled && disableEquipAnimationSetting.on

	@JvmStatic
	fun disableHandMovement(): Boolean = enabled && disableHandMovementSetting.on

	@JvmStatic
	fun swingDuration(entity: LivingEntity, original: Int): Int {
		if (!enabled || entity !== Minecraft.getInstance().player) return original
		val hand = entity.swingingArm ?: InteractionHand.MAIN_HAND
		val itemStack = entity.getItemInHand(hand)
		if (itemStack.isEmpty) return original
		val duration = if (ignoreHasteSetting.on) itemStack.swingAnimation.duration() else original
		return adjustedSwingDuration(duration, swingSpeedSetting.amount)
	}

	internal fun adjustedSwingDuration(duration: Int, speed: Double): Int {
		if (speed == 0.0) return max(duration, MIN_SWING_DURATION)
		return max((duration * exp(-speed)).toInt(), MIN_SWING_DURATION)
	}

	private fun resetControls() {
		for (setting in settings) setting.reset()
		persist()
	}

	private fun xRotation(degrees: Double): Quaternionf {
		if (degrees != cachedRotationX) {
			xRotation.rotationX(Math.toRadians(degrees).toFloat())
			cachedRotationX = degrees
		}
		return xRotation
	}

	private fun yRotation(degrees: Double): Quaternionf {
		if (degrees != cachedRotationY) {
			yRotation.rotationY(Math.toRadians(degrees).toFloat())
			cachedRotationY = degrees
		}
		return yRotation
	}

	private fun zRotation(degrees: Double): Quaternionf {
		if (degrees != cachedRotationZ) {
			zRotation.rotationZ(Math.toRadians(degrees).toFloat())
			cachedRotationZ = degrees
		}
		return zRotation
	}

	private const val MIN_SWING_DURATION = 1
	private const val TERMINATOR = "TERMINATOR"
}
