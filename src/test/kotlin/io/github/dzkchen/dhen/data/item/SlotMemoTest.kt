package io.github.dzkchen.dhen.data.item

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SlotMemoTest {
	@Test
	fun `the same custom data instance is parsed once`() {
		val stack = stack("ASPECT_OF_THE_END")

		assertSame(SkyBlockItems.of(stack), SkyBlockItems.of(stack))
	}

	@Test
	fun `two equal but distinct components are two records, because the cache is keyed on identity`() {
		val first = stack("ASPECT_OF_THE_END")
		val second = stack("ASPECT_OF_THE_END")

		assertEquals(first.get(DataComponents.CUSTOM_DATA), second.get(DataComponents.CUSTOM_DATA))
		assertNotSame(SkyBlockItems.of(first), SkyBlockItems.of(second))
	}

	@Test
	fun `a slot the server did not touch is one identity comparison`() {
		val memo = SkyBlockItems.memo(SLOTS)
		val stack = stack("HYPERION")

		assertSame(memo.of(0, stack), memo.of(0, stack))
		assertSame(SkyBlockItems.of(stack), memo.of(0, stack))
	}

	@Test
	fun `a container resent with equal contents is not parsed again`() {
		val memo = SkyBlockItems.memo(SLOTS)
		val first = memo.of(0, stack("HYPERION"))
		val resent = memo.of(0, stack("HYPERION"))

		assertSame(first, resent)
	}

	@Test
	fun `a slot whose contents actually changed is parsed again`() {
		val memo = SkyBlockItems.memo(SLOTS)
		val first = memo.of(0, stack("HYPERION"))
		val replaced = memo.of(0, stack("TERMINATOR"))

		assertNotSame(first, replaced)
		assertEquals("TERMINATOR", replaced.id)
	}

	@Test
	fun `every slot keeps its own record`() {
		val memo = SkyBlockItems.memo(SLOTS)
		val sword = memo.of(0, stack("HYPERION"))
		val bow = memo.of(1, stack("TERMINATOR"))

		assertEquals("HYPERION", memo.of(0, stack("HYPERION")).id)
		assertSame(sword, memo.of(0, stack("HYPERION")))
		assertSame(bow, memo.of(1, stack("TERMINATOR")))
	}

	@Test
	fun `an empty slot memoises the shared record`() {
		val memo = SkyBlockItems.memo(SLOTS)

		assertSame(SkyBlockItem.NONE, memo.of(0, ItemStack.EMPTY))
		assertSame(SkyBlockItem.NONE, memo.of(0, ItemStack.EMPTY))
	}

	@Test
	fun `a slot past the end of the memo still resolves`() {
		val memo = SkyBlockItems.memo(SLOTS)
		val stack = stack("HYPERION")

		assertEquals("HYPERION", memo.of(SLOTS, stack).id)
		assertEquals("HYPERION", memo.of(-1, stack).id)
	}

	@Test
	fun `a hit always answers with the record of the key it was asked for`() {
		val data = List(FLOOD) { ItemFixture.customData { putString("id", "FLOOD_ITEM_$it") } }

		data.forEachIndexed { index, entry ->
			assertEquals("FLOOD_ITEM_$index", SkyBlockItems.of(entry).id)
			assertSame(SkyBlockItems.of(entry), SkyBlockItems.of(entry))
		}
		data.forEachIndexed { index, entry -> assertEquals("FLOOD_ITEM_$index", SkyBlockItems.of(entry).id) }
	}

	@Test
	fun `the shared table is bounded and evicts rather than growing`() {
		val stacks = List(FLOOD) { stack("FLOOD_ITEM_$it") }
		val records = stacks.map(SkyBlockItems::of)
		val survivors = stacks.indices.count { records[it] === SkyBlockItems.of(stacks[it]) }

		assertTrue(SkyBlockItems.cachedRecords <= CAPACITY) { "the table grew to ${SkyBlockItems.cachedRecords} entries" }
		assertTrue(survivors <= CAPACITY) { "$survivors of $FLOOD records survived a table of $CAPACITY" }
		assertTrue(survivors < FLOOD)
	}

	private fun stack(id: String): ItemStack = ItemFixture.identified(id)

	private companion object {
		private const val SLOTS = 54
		private const val CAPACITY = 512
		private const val FLOOD = 4096

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
