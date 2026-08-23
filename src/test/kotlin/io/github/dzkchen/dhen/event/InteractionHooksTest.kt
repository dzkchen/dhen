package io.github.dzkchen.dhen.event

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InteractionHooksTest {
	private val bus = EventBus()

	@BeforeEach
	fun install() {
		InteractionHooks.install(bus)
	}

	@AfterEach
	fun uninstall() {
		InteractionHooks.uninstall()
	}

	@Test
	fun `a left click reaches its handler and swings by default`() {
		var seen = 0
		bus.subscribe<InteractionEvent.Attack> { seen++ }

		assertFalse(InteractionHooks.attack())
		assertEquals(1, seen)
	}

	@Test
	fun `cancelling a left click stops the attack`() {
		bus.subscribe<InteractionEvent.Attack> { it.cancel() }

		assertTrue(InteractionHooks.attack())
	}

	@Test
	fun `one cancelled left click leaves the next one alone`() {
		val seen = mutableListOf<InteractionEvent.Attack>()
		var cancelling = true
		bus.subscribe<InteractionEvent.Attack> {
			seen += it
			if (cancelling) it.cancel()
		}

		assertTrue(InteractionHooks.attack())
		cancelling = false
		assertFalse(InteractionHooks.attack())
		assertNotSame(seen[0], seen[1])
	}

	@Test
	fun `a left click with no subscriber never reaches the bus`() {
		var seen = 0
		val handle = bus.subscribe<InteractionEvent.Attack> { seen++ }

		InteractionHooks.attack()
		handle.unsubscribe()
		InteractionHooks.attack()

		assertEquals(1, seen)
	}

	@Test
	fun `uninstalling stops the interaction events`() {
		var seen = 0
		bus.subscribe<InteractionEvent.Attack> { seen++ }

		InteractionHooks.uninstall()

		assertFalse(InteractionHooks.attack())
		assertEquals(0, seen)
		assertFalse(InteractionHooks.active())
	}

	@Test
	fun `a throwing handler latches the interaction events off`() {
		bus.subscribe<InteractionEvent.Attack> { error("boom") }

		assertFalse(InteractionHooks.attack())

		assertFalse(InteractionHooks.active())
	}

	@Test
	fun `a used block carries its hand hit and the result vanilla returned`() {
		val seen = mutableListOf<InteractionEvent.UsedBlock>()
		bus.subscribe<InteractionEvent.UsedBlock> { seen += it }

		InteractionHooks.usedBlock(InteractionHand.MAIN_HAND, HIT, InteractionResult.SUCCESS)
		InteractionHooks.usedBlock(InteractionHand.OFF_HAND, HIT, InteractionResult.PASS)

		assertSame(HIT, seen[0].hit)
		assertEquals(InteractionHand.MAIN_HAND, seen[0].hand)
		assertEquals(InteractionResult.SUCCESS, seen[0].result)
		assertEquals(InteractionHand.OFF_HAND, seen[1].hand)
		assertEquals(InteractionResult.PASS, seen[1].result)
		assertFalse(Cancellable::class.java.isAssignableFrom(InteractionEvent.UsedBlock::class.java))
	}

	@Test
	fun `a used block with no subscriber never reaches the bus`() {
		var seen = 0
		val handle = bus.subscribe<InteractionEvent.UsedBlock> { seen++ }

		InteractionHooks.usedBlock(InteractionHand.MAIN_HAND, HIT, InteractionResult.PASS)
		handle.unsubscribe()
		InteractionHooks.usedBlock(InteractionHand.MAIN_HAND, HIT, InteractionResult.PASS)

		assertEquals(1, seen)
	}

	@Test
	fun `a throwing used block handler latches the interaction events off`() {
		bus.subscribe<InteractionEvent.UsedBlock> { error("boom") }

		InteractionHooks.usedBlock(InteractionHand.MAIN_HAND, HIT, InteractionResult.SUCCESS)

		assertFalse(InteractionHooks.active())
	}

	private companion object {
		private val POS = BlockPos(12, 70, -4)
		private val HIT = BlockHitResult(Vec3.ZERO, Direction.UP, POS, false)
	}
}
