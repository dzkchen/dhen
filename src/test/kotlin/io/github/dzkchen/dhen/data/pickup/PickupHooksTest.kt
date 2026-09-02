package io.github.dzkchen.dhen.data.pickup

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.ItemPickupEvent
import io.github.dzkchen.dhen.event.PickupSource
import io.github.dzkchen.dhen.event.PurseChangeEvent
import io.github.dzkchen.dhen.util.NO_DIGITS
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PickupHooksTest {
	private lateinit var bus: EventBus
	private lateinit var channels: PickupHooks.Channels
	private val pickups = mutableListOf<ItemPickupEvent>()
	private val purses = mutableListOf<Long>()

	@BeforeEach
	fun prepare() {
		bus = EventBus()
		channels = PickupHooks.Channels(bus)
		pickups.clear()
		purses.clear()
		bus.subscribe<ItemPickupEvent> { pickups += it }
		bus.subscribe<PurseChangeEvent> { purses += it.delta }
	}

	@Test
	fun `the first inventory read primes the snapshot without reporting anything`() {
		update(slot(0, "MITHRIL", 16))

		assertTrue(pickups.isEmpty())
		assertTrue(purses.isEmpty())
	}

	@Test
	fun `a new stack reports its whole count and a later count change reports the difference`() {
		update()
		update(slot(0, "MITHRIL", 16))
		update(slot(0, "MITHRIL", 48))

		assertEquals(listOf(16, 32), pickups.map { it.delta })
		assertEquals("MITHRIL", pickups[0].id)
		assertEquals("", pickups[0].uuid)
		assertEquals(PickupSource.INVENTORY, pickups[0].source)
	}

	@Test
	fun `a stack that leaves the inventory reports its whole count as a loss`() {
		update(slot(0, "MITHRIL", 16))
		update()

		assertEquals(listOf(-16), pickups.map { it.delta })
		assertEquals("MITHRIL", pickups[0].id)
	}

	@Test
	fun `equal keys in separate slots are summed before the comparison`() {
		update()
		update(slot(0, "MITHRIL", 16), slot(1, "MITHRIL", 8))

		assertEquals(listOf(24), pickups.map { it.delta })
	}

	@Test
	fun `two unique items sharing an id each report their own loss`() {
		val first = unique("HYPERION", "aaaa")
		val second = unique("HYPERION", "bbbb")
		update(first at 0, second at 1)
		update()

		assertEquals(listOf(-1, -1), pickups.map { it.delta })
		assertEquals(listOf("HYPERION", "HYPERION"), pickups.map { it.id })
		assertEquals(listOf("aaaa", "bbbb"), pickups.map { it.uuid })
	}

	@Test
	fun `a unique item carries its uuid and its Hypixel creation stamp`() {
		update()
		update(unique("HYPERION", "aaaa", created = 1234L) at 0)

		assertEquals("aaaa", pickups[0].uuid)
		assertEquals(1234L, pickups[0].createdAt)
		assertEquals(1, pickups[0].delta)
	}

	@Test
	fun `the SkyBlock menu slot never enters the comparison`() {
		update()
		update(slot(8, "SKYBLOCK_MENU", 1))

		assertTrue(pickups.isEmpty())
	}

	@Test
	fun `a held cursor stack freezes the comparison until the cursor is empty again`() {
		update(slot(0, "MITHRIL", 16))
		channels.slotUpdated(inventory(), ItemFixture.vanilla(), NO_PURSE)

		assertTrue(pickups.isEmpty())

		update()

		assertEquals(listOf(-16), pickups.map { it.delta })
	}

	@Test
	fun `the purse keeps reporting while the cursor holds a stack`() {
		channels.slotUpdated(inventory(), ItemStack.EMPTY, 1_000L)
		channels.slotUpdated(inventory(), ItemFixture.vanilla(), 2_000L)
		channels.slotUpdated(inventory(), ItemFixture.vanilla(), 1_500L)

		assertEquals(listOf(1_000L, -500L), purses)
		assertTrue(pickups.isEmpty())
	}

	@Test
	fun `an enchanted book whose lore names Chimera collapses to one purple entry`() {
		update()
		update(chimera("cccc") at 0)

		assertEquals("CHIMERA", pickups[0].id)
		assertEquals("§d§lChimera", pickups[0].name)
		assertEquals("cccc", pickups[0].uuid)
	}

	@Test
	fun `two Chimera books count as one entry of two`() {
		update()
		update(chimera("cccc") at 0, chimera("dddd") at 1)

		assertEquals(listOf(2), pickups.map { it.delta })
	}

	@Test
	fun `the purse reports its change only once both readings are known`() {
		channels.slotUpdated(inventory(), ItemStack.EMPTY, NO_PURSE)
		channels.slotUpdated(inventory(slot(0, "MITHRIL", 1)), ItemStack.EMPTY, NO_PURSE)

		assertTrue(purses.isEmpty())

		channels.slotUpdated(inventory(slot(0, "MITHRIL", 2)), ItemStack.EMPTY, 1_000L)
		channels.slotUpdated(inventory(slot(0, "MITHRIL", 3)), ItemStack.EMPTY, 1_250L)
		channels.slotUpdated(inventory(slot(0, "MITHRIL", 4)), ItemStack.EMPTY, 900L)

		assertEquals(listOf(250L, -350L), purses)
	}

	@Test
	fun `a world reset primes the next inventory read instead of reporting it`() {
		update(slot(0, "MITHRIL", 16))
		channels.reset()
		update()

		assertTrue(pickups.isEmpty())
		assertTrue(purses.isEmpty())
	}

	@Test
	fun `a sack message reports every amount its hover text lists`() {
		channels.sacked(sackMessage("+1,024 Mithril (12,345)\n+7 Titanium Ore (1,238)"))

		assertEquals(listOf(1024, 7), pickups.map { it.delta })
		assertEquals(listOf("Mithril", "Titanium Ore"), pickups.map { it.name })
		assertTrue(pickups.all { it.source == PickupSource.SACK })
	}

	@Test
	fun `only a sacks line that mentions an item counts as a sack message`() {
		assertTrue(PickupHooks.sackItemMessage("[Sacks] +64 items"))
		assertFalse(PickupHooks.sackItemMessage("[Sacks] +64 Mithril"))
		assertFalse(PickupHooks.sackItemMessage("You bought 3 items from the Bazaar"))
	}

	private fun update(vararg placed: Placed) {
		channels.slotUpdated(inventory(*placed), ItemStack.EMPTY, NO_PURSE)
	}

	private fun inventory(vararg placed: Placed): List<ItemStack> {
		val slots = MutableList(Inventory.INVENTORY_SIZE) { ItemStack.EMPTY }
		for (entry in placed) slots[entry.slot] = entry.stack
		return slots
	}

	private fun slot(slot: Int, id: String, count: Int): Placed =
		Placed(slot, ItemFixture.identified(id).copyWithCount(count))

	private fun unique(id: String, uuid: String, created: Long = 0L): ItemStack = ItemFixture.stack {
		putString("id", id)
		putString("uuid", uuid)
		putLong("timestamp", created)
	}

	private fun chimera(uuid: String): ItemStack = unique("ENCHANTED_BOOK", uuid).also {
		it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("Chimera I"))))
	}

	private fun sackMessage(hover: String): Component = Component.literal("[Sacks] ").append(
		Component.literal("+1,031 items").withStyle { style ->
			style.withHoverEvent(HoverEvent.ShowText(Component.literal(hover)))
		}
	)

	private infix fun ItemStack.at(slot: Int): Placed = Placed(slot, this)

	private class Placed(val slot: Int, val stack: ItemStack)

	private companion object {
		const val NO_PURSE = NO_DIGITS

		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
