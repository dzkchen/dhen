package io.github.dzkchen.dhen.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.joml.Matrix4f

class WorldDrawTest {
	@Test
	fun `camera offset is subtracted before coordinates narrow to floats`() {
		assertEquals(0.25f, cameraRelative(30_000_000.25, 30_000_000.0))
		assertEquals(-2.5f, cameraRelative(-12.0, -9.5))
	}

	@Test
	fun `zero-length lines are identifiable before their normal is divided`() {
		assertEquals(0f, worldLineSpan(0f, 0f, 0f))
		assertEquals(13f, worldLineSpan(3f, 4f, 12f))
	}

	@Test
	fun `line layers preserve alpha with depth and select the depth-off pipeline`() {
		assertEquals(WorldLineLayer.OPAQUE, worldLineLayer(WorldDepth.TESTED, 0xFF336699.toInt()))
		assertEquals(WorldLineLayer.TRANSLUCENT, worldLineLayer(WorldDepth.TESTED, 0x80336699.toInt()))
		assertEquals(WorldLineLayer.THROUGH_WALLS, worldLineLayer(WorldDepth.THROUGH_WALLS, 0xFF336699.toInt()))
	}

	@Test
	fun `filled layers select vanilla depth or the depth-off pipeline`() {
		assertEquals(WorldFillLayer.TESTED, worldFillLayer(WorldDepth.TESTED))
		assertEquals(WorldFillLayer.THROUGH_WALLS, worldFillLayer(WorldDepth.THROUGH_WALLS))
	}

	@Test
	fun `world text depth selects only verified display modes`() {
		assertEquals(Font.DisplayMode.NORMAL, worldTextDisplayMode(WorldDepth.TESTED))
		assertEquals(Font.DisplayMode.SEE_THROUGH, worldTextDisplayMode(WorldDepth.THROUGH_WALLS))
	}

	@Test
	fun `beacon radius follows vanilla distance and scoping rules`() {
		assertEquals(1f, worldBeaconRadiusScale(0.0, 0.0, 95.0, 0.0, false))
		assertEquals(2f, worldBeaconRadiusScale(0.0, 0.0, 192.0, 0.0, false))
		assertEquals(1f, worldBeaconRadiusScale(0.0, 0.0, 192.0, 0.0, true))
	}

	@Test
	fun `quad yaw produces a horizontal unit right vector`() {
		assertEquals(1f, worldQuadRightX(0f), 0.00001f)
		assertEquals(0f, worldQuadRightZ(0f), 0.00001f)
		assertEquals(0f, worldQuadRightX(90f), 0.00001f)
		assertEquals(1f, worldQuadRightZ(90f), 0.00001f)
	}

	@Test
	fun `scoped world pose restores after success and failure`() {
		val pose = PoseStack()
		val initial = Matrix4f(pose.last().pose())
		withRestoredWorldPose(pose) {
			translate(1f, 2f, 3f)
		}
		assertEquals(initial, Matrix4f(pose.last().pose()))
		assertThrows(IllegalStateException::class.java) {
			withRestoredWorldPose(pose) {
				translate(4f, 5f, 6f)
				pushPose()
				translate(7f, 8f, 9f)
				throw IllegalStateException()
			}
		}
		assertEquals(initial, Matrix4f(pose.last().pose()))
	}
}
