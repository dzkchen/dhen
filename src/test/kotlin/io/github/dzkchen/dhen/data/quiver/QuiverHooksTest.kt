package io.github.dzkchen.dhen.data.quiver

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.QuiverUpdateEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QuiverHooksTest {
	private lateinit var bus: EventBus
	private lateinit var channels: QuiverHooks.Channels
	private var updates = 0

	@BeforeEach
	fun prepare() {
		QuiverState.reset()
		bus = EventBus()
		channels = QuiverHooks.Channels(bus, inSkyBlock = { true })
		bus.subscribe<QuiverUpdateEvent> { updates++ }
		updates = 0
	}

	@AfterEach
	fun reset() {
		QuiverState.reset()
	}

	@Test
	fun `the source arrow table is complete and keeps its exact ids`() {
		assertEquals("Flint Arrow", QuiverArrow.byId("ARROW")?.displayName)
		assertEquals("NANSORB_ARROW", QuiverArrow.byName("Nansorb Arrow")?.id)
		assertNull(QuiverArrow.byName("Unknown Arrow"))
	}

	@Test
	fun `slot 44 active-arrow lore updates once and accepts grouped amounts`() {
		val menu = lored("SKYBLOCK_MENU", "Active Arrow: Flint Arrow (2,880)")

		channels.ownSlot(menu)
		channels.ownSlot(menu)

		assertEquals(QuiverArrow.FLINT, QuiverState.currentArrow)
		assertEquals(2880, QuiverState.currentAmount)
		assertEquals(1, updates)
	}

	@Test
	fun `the quiver preview requires its attribute and resolves the arrow name`() {
		val preview = ItemFixture.stack {
			putString("id", "QUIVER_PREVIEW")
			putBoolean("quiver_arrow", true)
		}.also {
			it.set(DataComponents.CUSTOM_NAME, Component.literal("Explosive Arrow"))
			it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("Arrows Remaining: 1,250"))))
		}

		channels.ownSlot(preview)

		assertEquals(QuiverArrow.EXPLOSIVE, QuiverState.currentArrow)
		assertEquals(1250, QuiverState.currentAmount)
	}

	@Test
	fun `source chat regexes select fill and exhaust the current arrow`() {
		channels.chatted(" \t§aYou set your selected arrow type to §R§fFlint Arrow§r§a!\n")
		channels.chatted("§aYou filled your quiver with §f1,253 §aextra arrows!")
		channels.chatted("§c§lQUIVER! §cYou have run out of §fFlint Arrows§c!")

		assertEquals(QuiverArrow.FLINT, QuiverState.currentArrow)
		assertEquals(0, QuiverState.currentAmount)
		assertEquals(3, updates)
	}

	@Test
	fun `jax and added-to-quiver lines reconcile counts by arrow type`() {
		channels.chatted("§aYou set your selected arrow type to §r§fFlint Arrow§r§a!")
		channels.chatted("§aJax forged §r§fFlint Arrow§r§8 x386 §r§afor §r§61,930 Coins§r§a!")
		channels.chatted("§aYou've added §r§fFlint Arrow x64 §r§ato your quiver!")

		assertEquals(450, QuiverState.currentAmount)
	}

	@Test
	fun `opening the Quiver replaces rather than duplicates every arrow count`() {
		channels.chatted("§aYou set your selected arrow type to §fFlint Arrow§a!")
		val event = ContainerReadyEvent(
			Component.literal("Quiver"),
			1,
			3,
			listOf(arrow("ARROW", 32), arrow("ARROW", 18), arrow("EXPLOSIVE_ARROW", 7))
		)

		channels.opened(event)
		channels.opened(event)

		assertEquals(50, QuiverState.currentAmount)
		assertEquals(7, QuiverState.amount(QuiverArrow.EXPLOSIVE))
		assertEquals(2, updates)
	}

	@Test
	fun `world reset clears arrow amounts and equipment state`() {
		channels.chatted("§aYou set your selected arrow type to §fFlint Arrow§a!")
		channels.chatted("§aYou filled your quiver with §f100 §aextra arrows!")

		channels.reset()

		assertNull(QuiverState.currentArrow)
		assertEquals(0, QuiverState.amount(QuiverArrow.FLINT))
	}

	private fun lored(id: String, vararg lines: String): ItemStack =
		ItemFixture.identified(id).also { it.set(DataComponents.LORE, ItemLore(lines.map(Component::literal))) }

	private fun arrow(id: String, count: Int): ItemStack = ItemFixture.identified(id).copyWithCount(count)

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
