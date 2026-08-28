package io.github.dzkchen.dhen.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
