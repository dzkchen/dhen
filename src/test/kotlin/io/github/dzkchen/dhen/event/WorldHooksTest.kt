package io.github.dzkchen.dhen.event

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WorldHooksTest {
	private val bus = EventBus()

	@BeforeEach
	fun install() {
		WorldHooks.install(bus)
	}

	@AfterEach
	fun uninstall() {
		WorldHooks.uninstall()
	}

	@Test
	fun `every connection phase publishes its own world change`() {
		val phases = mutableListOf<WorldChange>()
		bus.subscribe<WorldChangeEvent> { phases += it.phase }

		WorldChange.entries.forEach(WorldHooks::worldChanged)

		assertEquals(listOf(WorldChange.INIT, WorldChange.JOIN, WorldChange.DISCONNECT), phases)
	}

	@Test
	fun `a block change carries the position and both states`() {
		var pos: BlockPos? = null
		var oldState: BlockState? = null
		var newState: BlockState? = null
		bus.subscribe<BlockChangeEvent> {
			pos = it.pos
			oldState = it.oldState
			newState = it.newState
		}

		WorldHooks.blockChanged(FIRST, STONE, DIRT)

		assertEquals(FIRST, pos)
		assertSame(STONE, oldState)
		assertSame(DIRT, newState)
	}

	@Test
	fun `every block change reuses one event instance`() {
		val seen = mutableListOf<BlockChangeEvent>()
		val positions = mutableListOf<BlockPos>()
		bus.subscribe<BlockChangeEvent> {
			seen += it
			positions += it.pos
		}

		WorldHooks.blockChanged(FIRST, STONE, DIRT)
		WorldHooks.blockChanged(SECOND, DIRT, STONE)

		assertSame(seen[0], seen[1])
		assertEquals(listOf(FIRST, SECOND), positions)
	}

	@Test
	fun `a block change with no subscriber never touches the shared event`() {
		var pos: BlockPos? = null
		var oldState: BlockState? = null
		val handle = bus.subscribe<BlockChangeEvent> {
			pos = it.pos
			oldState = it.oldState
		}

		WorldHooks.blockChanged(FIRST, STONE, DIRT)
		handle.unsubscribe()
		WorldHooks.blockChanged(SECOND, DIRT, STONE)

		assertEquals(FIRST, pos)
		assertSame(STONE, oldState)
	}

	@Test
	fun `a block change releases its position and states when dispatch returns`() {
		var seen: BlockChangeEvent? = null
		bus.subscribe<BlockChangeEvent> { seen = it }

		WorldHooks.blockChanged(FIRST, STONE, DIRT)

		assertThrows(NullPointerException::class.java) { seen!!.pos }
		assertThrows(NullPointerException::class.java) { seen!!.oldState }
		assertThrows(NullPointerException::class.java) { seen!!.newState }
	}

	@Test
	fun `a retained position survives the caller reusing its scratch position`() {
		val scratch = BlockPos.MutableBlockPos(1, 2, 3)
		var retained: BlockPos? = null
		bus.subscribe<BlockChangeEvent> { retained = it.retainedPos() }

		WorldHooks.blockChanged(scratch, STONE, DIRT)
		scratch.set(9, 9, 9)

		assertEquals(BlockPos(1, 2, 3), retained)
	}

	@Test
	fun `uninstalling stops the world change and block change events`() {
		val seen = mutableListOf<Event>()
		bus.subscribe<WorldChangeEvent> { seen += it }
		bus.subscribe<BlockChangeEvent> { seen += it }

		WorldHooks.uninstall()
		WorldHooks.worldChanged(WorldChange.DISCONNECT)
		WorldHooks.blockChanged(FIRST, STONE, DIRT)

		assertTrue(seen.isEmpty())
		assertFalse(WorldHooks.active())
	}

	@Test
	fun `a throwing handler latches world events off`() {
		bus.subscribe<BlockChangeEvent> { error("boom") }

		WorldHooks.blockChanged(FIRST, STONE, DIRT)

		assertFalse(WorldHooks.active())
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()

		private val FIRST = BlockPos(4, 64, 8)
		private val SECOND = BlockPos(5, 65, 9)
		private val STONE: BlockState = Blocks.STONE.defaultBlockState()
		private val DIRT: BlockState = Blocks.DIRT.defaultBlockState()
	}
}
