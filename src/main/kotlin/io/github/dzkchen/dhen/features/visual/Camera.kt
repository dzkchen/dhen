package io.github.dzkchen.dhen.features.visual

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.config.KeybindSetting
import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.config.Setting.Companion.withDependency
import io.github.dzkchen.dhen.data.SkyBlockLocation
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.PacketReceiveEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.render.IrisCompat
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.state.LightmapRenderState
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import org.joml.Vector3f
import org.joml.Vector3fc

object Camera : Module(
	name = "Camera",
	category = Category.VISUAL,
	description = "Controls camera perspective, lighting, overlays, and visual effects."
) {
	internal val fullBrightSetting = BooleanSetting("Full Bright")
	private var fullBright by fullBrightSetting

	internal val fullBrightKeybindSetting = KeybindSetting("Full Bright Keybind").onPress {
		fullBrightSetting.on = !fullBrightSetting.on
		persist()
	}
	private var fullBrightKeybind by fullBrightKeybindSetting

	internal val useGammaSetting = BooleanSetting("Use Gamma").withDependency { fullBrightSetting.on }
	private var useGamma by useGammaSetting

	internal val strengthSetting = NumberSetting("Strength", 100.0, 0.0, 100.0).withDependency { fullBrightSetting.on }
	private var strength by strengthSetting

	internal val eyeHeightFixSetting = BooleanSetting("Eye Height Fix")
	private var eyeHeightFix by eyeHeightFixSetting

	internal val skyBlockOnlySetting = BooleanSetting("SkyBlock Only").withDependency { eyeHeightFixSetting.on }
	private var skyBlockOnly by skyBlockOnlySetting

	internal val disableFrontCameraSetting = BooleanSetting("Disable Front Camera")
	private var disableFrontCamera by disableFrontCameraSetting

	internal val cameraClipSetting = BooleanSetting("Camera Clip")
	private var cameraClip by cameraClipSetting

	internal val customCameraDistanceSetting = BooleanSetting("Custom Camera Distance")
	private var customCameraDistance by customCameraDistanceSetting

	internal val cameraDistanceSetting = NumberSetting("Camera Distance", 4.0, 1.0, 10.0, 0.1)
		.withDependency { customCameraDistanceSetting.on }
	private var cameraDistance by cameraDistanceSetting

	internal val doubleSneakFixSetting = BooleanSetting("Double Sneak Fix")
	private var doubleSneakFix by doubleSneakFixSetting

	internal val ridingInputDelayFixSetting = BooleanSetting("Riding Input Delay Fix")
	private var ridingInputDelayFix by ridingInputDelayFixSetting

	internal val hideFireOverlaySetting = BooleanSetting("Hide Fire Overlay")
	private var hideFireOverlay by hideFireOverlaySetting

	internal val hidePortalOverlaySetting = BooleanSetting("Hide Portal Overlay")
	private var hidePortalOverlay by hidePortalOverlaySetting

	internal val hideWaterOverlaySetting = BooleanSetting("Hide Water Overlay")
	private var hideWaterOverlay by hideWaterOverlaySetting

	internal val hideBlockOverlaySetting = BooleanSetting("Hide Block Overlay")
	private var hideBlockOverlay by hideBlockOverlaySetting

	internal val disableBlindnessSetting = BooleanSetting("Disable Blindness")
	private var disableBlindness by disableBlindnessSetting

	internal val disableNauseaSetting = BooleanSetting("Disable Nausea")
	private var disableNausea by disableNauseaSetting

	internal val customFovSetting = BooleanSetting("Custom FOV")
	private var customFov by customFovSetting

	internal val fovSetting = NumberSetting("FOV", 110.0, 30.0, 179.0).withDependency { customFovSetting.on }
	private var fov by fovSetting

	private var shadersActive = false

	init {
		on<ClientTickEvent.End> {
			shadersActive = IrisCompat.shadersActive()
		}
		on<PacketReceiveEvent.Pre> { event ->
			if (!doubleSneakFixSetting.on) return@on
			val packet = event.packet as? ClientboundSetEntityDataPacket ?: return@on
			val player = Minecraft.getInstance().player ?: return@on
			filterPosePacket(packet, player.id, player.isShiftKeyDown)
		}
	}

	override fun onEnabled() {
		shadersActive = IrisCompat.shadersActive()
	}

	override fun onDisabled() {
		shadersActive = false
	}

	@JvmStatic
	fun replaceCameraType(type: CameraType): CameraType =
		if (enabled && disableFrontCameraSetting.on && type == CameraType.THIRD_PERSON_FRONT) CameraType.FIRST_PERSON else type

	@JvmStatic
	fun cameraDistance(original: Double): Double =
		if (enabled && customCameraDistanceSetting.on) cameraDistanceSetting.amount else original

	@JvmStatic
	fun maxZoom(original: Float, requested: Float): Float =
		if (enabled && cameraClipSetting.on) requested else original

	@JvmStatic
	fun eyeHeight(original: Float): Float =
		if (shouldFixEyeHeight() && original == MODERN_SNEAK_EYE_HEIGHT) LEGACY_SNEAK_EYE_HEIGHT else original

	@JvmStatic
	fun fov(original: Float): Float {
		if (!enabled || !customFovSetting.on) return original
		val vanilla = Minecraft.getInstance().options.fov().get().toFloat()
		return original * fovSetting.amount.toFloat() / vanilla
	}

	@JvmStatic
	fun cullingFov(original: Float, rendered: Float): Float =
		if (enabled && customFovSetting.on) maxOf(rendered, fovSetting.amount.toFloat()) else original

	@JvmStatic
	fun shouldHideFireOverlay(): Boolean = enabled && hideFireOverlaySetting.on

	@JvmStatic
	fun shouldHidePortalOverlay(): Boolean = enabled && hidePortalOverlaySetting.on

	@JvmStatic
	fun shouldHideWaterOverlay(): Boolean = enabled && hideWaterOverlaySetting.on

	@JvmStatic
	fun shouldHideBlockOverlay(): Boolean = enabled && hideBlockOverlaySetting.on

	@JvmStatic
	fun shouldDisableBlindness(): Boolean = enabled && disableBlindnessSetting.on

	@JvmStatic
	fun nauseaIntensity(original: Float): Float =
		if (enabled && disableNauseaSetting.on) 0.0f else original

	@JvmStatic
	fun fixRidingTurn(vehicle: Entity, passenger: Entity) {
		if (!enabled || !ridingInputDelayFixSetting.on || !passenger.isAlwaysTicking) return
		passenger.setYBodyRot(vehicle.yRot)
		passenger.yHeadRot = passenger.yRot
	}

	@JvmStatic
	fun applyFullBright(state: LightmapRenderState): LightmapRenderState {
		if (!state.needsUpdate || !enabled || !fullBrightSetting.on) return state
		val amount = (strengthSetting.amount / 100.0).toFloat()
		state.darknessEffectScale = Mth.lerp(amount, state.darknessEffectScale, 0.0f)
		if (usesGamma(shadersActive)) {
			state.brightness = amount * GAMMA_SCALE
			return state
		}
		state.skyFactor = Mth.lerp(amount, state.skyFactor, 1.0f)
		state.blockFactor = Mth.lerp(amount, state.blockFactor, 1.0f)
		state.nightVisionEffectIntensity = Mth.lerp(amount, state.nightVisionEffectIntensity, 0.0f)
		state.bossOverlayWorldDarkening = Mth.lerp(amount, state.bossOverlayWorldDarkening, 0.0f)
		state.brightness = Mth.lerp(amount, state.brightness, 1.0f)
		state.blockLightTint = brighten(state.blockLightTint, amount)
		state.skyLightColor = brighten(state.skyLightColor, amount)
		state.ambientColor = brighten(state.ambientColor, amount)
		state.nightVisionColor = brighten(state.nightVisionColor, amount)
		return state
	}

	internal fun shouldFixEyeHeight(inSkyBlock: Boolean = SkyBlockLocation.inSkyBlock): Boolean =
		enabled && eyeHeightFixSetting.on && (!skyBlockOnlySetting.on || inSkyBlock)

	internal fun usesGamma(shaderActive: Boolean): Boolean = useGammaSetting.on || shaderActive

	internal fun filterPosePacket(packet: ClientboundSetEntityDataPacket, localPlayerId: Int, sneaking: Boolean): Int {
		if (!enabled || !doubleSneakFixSetting.on || packet.id != localPlayerId) return 0
		return filterPoseValues(packet.packedItems, sneaking)
	}

	internal fun filterPoseValues(values: MutableList<SynchedEntityData.DataValue<*>>, sneaking: Boolean): Int {
		var removed = 0
		for (index in values.lastIndex downTo 0) {
			val value = values[index]
			if (value.serializer !== EntityDataSerializers.POSE) continue
			val pose = value.value as Pose
			if ((sneaking && pose == Pose.STANDING) || (!sneaking && pose == Pose.CROUCHING)) {
				values.removeAt(index)
				removed++
			}
		}
		return removed
	}

	private fun brighten(color: Vector3fc, amount: Float): Vector3fc {
		if (amount == 1.0f) return WHITE
		if (color is Vector3f) color.lerp(WHITE, amount)
		return color
	}

	private val WHITE: Vector3fc = Vector3f(1.0f, 1.0f, 1.0f)

	private const val GAMMA_SCALE = 15.0f
	private const val MODERN_SNEAK_EYE_HEIGHT = 1.27f
	private const val LEGACY_SNEAK_EYE_HEIGHT = 1.54f
}
