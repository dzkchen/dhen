package io.github.dzkchen.dhen.features.visual

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.config.SettingCodec
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.renderer.state.LightmapRenderState
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Pose
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CameraTest {
	@AfterEach
	fun reset() {
		for (setting in Camera.settings) setting.reset()
	}

	@Test
	fun `declares the complete visual module control surface`() {
		assertEquals("Camera", Camera.name)
		assertEquals(Category.VISUAL, Camera.category)
		assertEquals(2, Camera.subscriptionCount)
		assertEquals(
			listOf(
				"Full Bright", "Full Bright Keybind", "Use Gamma", "Strength",
				"Eye Height Fix", "SkyBlock Only", "Disable Front Camera", "Camera Clip",
				"Custom Camera Distance", "Camera Distance", "Double Sneak Fix", "Riding Input Delay Fix",
				"Hide Fire Overlay", "Hide Portal Overlay", "Hide Water Overlay", "Hide Block Overlay",
				"Disable Blindness", "Disable Nausea", "Custom FOV", "FOV"
			),
			Camera.settings.map { it.name }
		)
		assertEquals(100.0, Camera.strengthSetting.default)
		assertEquals(4.0, Camera.cameraDistanceSetting.default)
		assertEquals(0.1, Camera.cameraDistanceSetting.step)
		assertEquals(110.0, Camera.fovSetting.default)
	}

	@Test
	fun `dependent controls follow their owning toggles`() {
		assertFalse(Camera.useGammaSetting.isVisible)
		assertFalse(Camera.strengthSetting.isVisible)
		assertFalse(Camera.skyBlockOnlySetting.isVisible)
		assertFalse(Camera.cameraDistanceSetting.isVisible)
		assertFalse(Camera.fovSetting.isVisible)

		Camera.fullBrightSetting.on = true
		Camera.eyeHeightFixSetting.on = true
		Camera.customCameraDistanceSetting.on = true
		Camera.customFovSetting.on = true

		assertTrue(Camera.useGammaSetting.isVisible)
		assertTrue(Camera.strengthSetting.isVisible)
		assertTrue(Camera.skyBlockOnlySetting.isVisible)
		assertTrue(Camera.cameraDistanceSetting.isVisible)
		assertTrue(Camera.fovSetting.isVisible)
	}

	@Test
	fun `all controls serialize and representative values round trip`() {
		Camera.fullBrightSetting.on = true
		Camera.useGammaSetting.on = true
		Camera.strengthSetting.amount = 37.0
		Camera.cameraDistanceSetting.amount = 7.4
		Camera.fovSetting.amount = 145.0
		val saved = SettingCodec.writeInto(JsonObject(), Camera.settings)

		assertEquals(Camera.settings.map { it.name }.toSet(), saved.keySet())
		for (setting in Camera.settings) setting.reset()
		SettingCodec.readInto(saved, Camera.settings, Camera.name)

		assertTrue(Camera.fullBrightSetting.on)
		assertTrue(Camera.useGammaSetting.on)
		assertEquals(37.0, Camera.strengthSetting.amount)
		assertEquals(7.4, Camera.cameraDistanceSetting.amount)
		assertEquals(145.0, Camera.fovSetting.amount)
	}

	@Test
	fun `eye height obeys module toggle and optional SkyBlock boundary`() = withCamera {
		Camera.eyeHeightFixSetting.on = true
		assertTrue(Camera.shouldFixEyeHeight(inSkyBlock = false))

		Camera.skyBlockOnlySetting.on = true
		assertFalse(Camera.shouldFixEyeHeight(inSkyBlock = false))
		assertTrue(Camera.shouldFixEyeHeight(inSkyBlock = true))
		Camera.skyBlockOnlySetting.on = false
		assertEquals(1.54f, Camera.eyeHeight(1.27f))
		assertEquals(1.62f, Camera.eyeHeight(1.62f))
	}

	@Test
	fun `double sneak drops only contradictory local pose values`() = withCamera {
		Camera.doubleSneakFixSetting.on = true
		val packet = packet(Pose.STANDING, Pose.SWIMMING)

		assertEquals(0, Camera.filterPosePacket(packet, localPlayerId = 4, sneaking = true))
		assertEquals(1, Camera.filterPosePacket(packet, localPlayerId = 7, sneaking = true))
		assertEquals(listOf(Pose.SWIMMING), packet.packedItems.map { it.value })

		val standing = packet(Pose.STANDING)
		assertEquals(0, Camera.filterPosePacket(standing, localPlayerId = 7, sneaking = false))
		assertEquals(listOf(Pose.STANDING), standing.packedItems.map { it.value })
	}

	@Test
	fun `native full bright blends existing state without replacing its holder`() = withCamera {
		Camera.fullBrightSetting.on = true
		Camera.strengthSetting.amount = 50.0
		val state = LightmapRenderState().apply {
			needsUpdate = true
			skyFactor = 0.2f
			blockFactor = 0.4f
			brightness = 0.0f
			darknessEffectScale = 0.6f
			blockLightTint = Vector3f(0.2f, 0.4f, 0.6f)
		}

		assertSame(state, Camera.applyFullBright(state))
		assertEquals(0.6f, state.skyFactor, 0.0001f)
		assertEquals(0.7f, state.blockFactor, 0.0001f)
		assertEquals(0.5f, state.brightness, 0.0001f)
		assertEquals(0.3f, state.darknessEffectScale, 0.0001f)
		assertEquals(0.6f, state.blockLightTint.x(), 0.0001f)
		assertEquals(0.7f, state.blockLightTint.y(), 0.0001f)
		assertEquals(0.8f, state.blockLightTint.z(), 0.0001f)
	}

	@Test
	fun `gamma routing covers explicit mode and active shaders while suppressing darkness`() = withCamera {
		assertFalse(Camera.usesGamma(shaderActive = false))
		assertTrue(Camera.usesGamma(shaderActive = true))

		Camera.useGammaSetting.on = true
		Camera.fullBrightSetting.on = true
		val state = LightmapRenderState().apply {
			needsUpdate = true
			skyFactor = 0.2f
			darknessEffectScale = 0.6f
		}
		Camera.applyFullBright(state)

		assertTrue(Camera.usesGamma(shaderActive = false))
		assertEquals(0.2f, state.skyFactor)
		assertEquals(0.0f, state.darknessEffectScale)
		assertEquals(15.0f, state.brightness)
	}

	private fun packet(vararg poses: Pose): ClientboundSetEntityDataPacket = ClientboundSetEntityDataPacket(
		7,
		poses.mapTo(mutableListOf<SynchedEntityData.DataValue<*>>()) { pose ->
			SynchedEntityData.DataValue(6, EntityDataSerializers.POSE, pose)
		}
	)

	private inline fun withCamera(block: () -> Unit) {
		val manager = ModuleManager()
		manager.register(Camera)
		try {
			manager.enable(Camera)
			block()
		} finally {
			manager.unregister(Camera)
		}
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			bootstrapMinecraft()
		}
	}
}
