package io.github.dzkchen.dhen.event

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
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
	fun `a block use handler is woken by nothing else`() {
		val seen = mutableListOf<InteractionEvent>()
		bus.subscribe<InteractionEvent.UseBlock> { seen += it }
		val useBlock = InteractionEvent.UseBlock(POS, InteractionHand.MAIN_HAND, ItemStack.EMPTY)

		bus.type<InteractionEvent.UseBlock>().dispatch(useBlock)
		bus.type<InteractionEvent.UseItem>().dispatch(InteractionEvent.UseItem(InteractionHand.MAIN_HAND, ItemStack.EMPTY))
		bus.type<InteractionEvent.AttackBlock>().dispatch(InteractionEvent.AttackBlock(POS))
		bus.type<InteractionEvent.Attack>().dispatch(InteractionEvent.Attack())

		assertEquals(listOf<InteractionEvent>(useBlock), seen)
	}

	@Test
	fun `a block use carries the position the hand and the held item`() {
		var seen: InteractionEvent.UseBlock? = null
		bus.subscribe<InteractionEvent.UseBlock> { seen = it }
		val held = ItemStack.EMPTY

		bus.type<InteractionEvent.UseBlock>().dispatch(InteractionEvent.UseBlock(POS, InteractionHand.OFF_HAND, held))

		assertEquals(POS, seen?.pos)
		assertEquals(InteractionHand.OFF_HAND, seen?.hand)
		assertSame(held, seen?.item)
	}

	@Test
	fun `a block attack carries the position`() {
		var seen: InteractionEvent.AttackBlock? = null
		bus.subscribe<InteractionEvent.AttackBlock> { seen = it }

		bus.type<InteractionEvent.AttackBlock>().dispatch(InteractionEvent.AttackBlock(POS))

		assertEquals(POS, seen?.pos)
	}

	@Test
	fun `every case starts uncancelled and remembers being cancelled`() {
		val cases = listOf(
			InteractionEvent.UseBlock(POS, InteractionHand.MAIN_HAND, ItemStack.EMPTY),
			InteractionEvent.UseItem(InteractionHand.MAIN_HAND, ItemStack.EMPTY),
			InteractionEvent.AttackBlock(POS),
			InteractionEvent.Attack()
		)

		assertTrue(cases.none { it.cancelled })
		cases.forEach { it.cancel() }
		assertTrue(cases.all { it.cancelled })
	}

	private companion object {
		private val POS = BlockPos(12, 70, -4)
	}
}
