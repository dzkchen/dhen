package io.github.dzkchen.dhen.event

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.SharedConstants
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.server.Bootstrap
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class WorldRenderHooksTest {
	private val bus = EventBus()

	@BeforeEach
	fun install() {
		WorldRenderHooks.install(bus)
	}

	@AfterEach
	fun uninstall() {
		WorldRenderHooks.uninstall()
	}

	@Test
	fun `a frame hands the handler the collector and pose the context carries`() {
		val frame = frame()
		var seen: WorldRenderEvent? = null
		bus.subscribe<WorldRenderEvent> { seen = it }

		WorldRenderHooks.render(frame)

		assertSame(frame.submitNodeCollector(), seen?.collector)
		assertSame(frame.poseStack(), seen?.pose)
	}

	@Test
	fun `a frame hands the handler the camera and game time of that frame`() {
		val frame = frame(gameTime = 7L, cameraPos = Vec3(10.0, 64.0, -5.0))
		var camera = Vec3.ZERO
		var gameTime = 0L
		bus.subscribe<WorldRenderEvent> {
			camera = it.camera.pos
			gameTime = it.gameTime
		}

		WorldRenderHooks.render(frame)

		assertEquals(Vec3(10.0, 64.0, -5.0), camera)
		assertEquals(7L, gameTime)
	}

	@Test
	fun `every frame reuses one event instance`() {
		val seen = mutableListOf<WorldRenderEvent>()
		bus.subscribe<WorldRenderEvent> { seen += it }

		WorldRenderHooks.render(frame(gameTime = 1L))
		WorldRenderHooks.render(frame(gameTime = 2L))

		assertSame(seen[0], seen[1])
		assertEquals(2L, seen[1].gameTime)
	}

	@Test
	fun `a frame with no subscriber never touches the shared event`() {
		var seen: WorldRenderEvent? = null
		val handle = bus.subscribe<WorldRenderEvent> { seen = it }

		WorldRenderHooks.render(frame(gameTime = 1L))
		handle.unsubscribe()
		WorldRenderHooks.render(frame(gameTime = 2L))

		assertEquals(1L, seen?.gameTime)
	}

	@Test
	fun `a frame published from inside a handler borrows its own event`() {
		val seen = mutableListOf<WorldRenderEvent>()
		var nested = false
		bus.subscribe<WorldRenderEvent> { event ->
			seen += event
			if (!nested) {
				nested = true
				WorldRenderHooks.render(frame(gameTime = 2L))
			}
		}

		WorldRenderHooks.render(frame(gameTime = 1L))

		assertNotSame(seen[0], seen[1])
		assertEquals(2L, seen[1].gameTime)
		assertEquals(1L, seen[0].gameTime)
	}

	@Test
	fun `a disconnect releases the frame the shared event was holding`() {
		var seen: WorldRenderEvent? = null
		bus.subscribe<WorldRenderEvent> { seen = it }
		WorldRenderHooks.render(frame())

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))

		assertThrows(NullPointerException::class.java) { seen!!.collector }
	}

	@Test
	fun `joining a world leaves the frame the shared event is holding alone`() {
		var seen: WorldRenderEvent? = null
		bus.subscribe<WorldRenderEvent> { seen = it }
		val frame = frame(gameTime = 3L)
		WorldRenderHooks.render(frame)

		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.JOIN))

		assertEquals(3L, seen?.gameTime)
	}

	@Test
	fun `uninstalling stops the world render event`() {
		var frames = 0
		bus.subscribe<WorldRenderEvent> { frames++ }

		WorldRenderHooks.uninstall()
		WorldRenderHooks.render(frame())

		assertEquals(0, frames)
		assertFalse(WorldRenderHooks.active())
		assertFalse(bus.type<WorldChangeEvent>().hasSubscribers)
	}

	@Test
	fun `a throwing handler latches world render off`() {
		bus.subscribe<WorldRenderEvent> { error("boom") }

		WorldRenderHooks.render(frame())

		assertFalse(WorldRenderHooks.active())
	}

	private fun frame(gameTime: Long = 0L, cameraPos: Vec3 = Vec3.ZERO): LevelRenderContext {
		val state = LevelRenderState()
		state.gameTime = gameTime
		state.cameraRenderState.pos = cameraPos
		return FakeFrame(noCollector(), PoseStack(), state)
	}

	private class FakeFrame(
		private val collector: SubmitNodeCollector,
		private val pose: PoseStack,
		private val state: LevelRenderState
	) : LevelRenderContext {
		override fun submitNodeCollector(): SubmitNodeCollector = collector

		override fun poseStack(): PoseStack = pose

		override fun levelState(): LevelRenderState = state

		override fun sectionsToRender(): ChunkSectionsToRender = throw UnsupportedOperationException()

		override fun gameRenderer(): GameRenderer = throw UnsupportedOperationException()

		override fun levelRenderer(): LevelRenderer = throw UnsupportedOperationException()
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			SharedConstants.tryDetectVersion()
			Bootstrap.bootStrap()
		}

		private fun noCollector(): SubmitNodeCollector = Proxy.newProxyInstance(
			SubmitNodeCollector::class.java.classLoader,
			arrayOf(SubmitNodeCollector::class.java)
		) { _, _, _ -> throw UnsupportedOperationException() } as SubmitNodeCollector
	}
}
