package io.github.dzkchen.dhen.data.maxwell

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.MaxwellUpdateEvent
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
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaxwellHooksTest {
	private lateinit var channels: MaxwellHooks.Channels
	private var updates = 0

	@BeforeAll
	fun bootstrap() = ItemFixture.bootstrap()

	@BeforeEach
	fun prepare() {
		MaxwellState.reset()
		val bus = EventBus()
		channels = MaxwellHooks.Channels(bus, inSkyBlock = { true }, inDungeon = { false })
		bus.subscribe<MaxwellUpdateEvent> { updates++ }
		updates = 0
	}

	@AfterEach
	fun clear() = MaxwellState.reset()

	@Test
	fun `the accessory bag reads its selected power and its magical power`() {
		channels.opened(
			menu("Your Bags", stack("Accessory Bag", "§7Selected Power: §aSighted", "§7Accessory Power: §6419"))
		)

		assertEquals("Sighted", MaxwellState.power)
		assertEquals(419, MaxwellState.magicalPower)
		assertEquals(1, updates)
	}

	@Test
	fun `an accessory bag with no power lore reports no power and no tunings`() {
		channels.opened(
			menu("Your Bags", stack("Accessory Bag", "§7Visit Maxwell in the Hub to learn"))
		)

		assertEquals(MaxwellState.NO_POWER, MaxwellState.power)
		assertEquals(0, MaxwellState.magicalPower)
		assertEquals(emptyList<PowerTuning>(), MaxwellState.tunings)
	}

	@Test
	fun `the thaumaturgy page reads the selected power, the total and the rounded tunings`() {
		channels.opened(
			menu(
				"(1/2) Accessory Bag Thaumaturgy",
				stack("Sighted", "§7Grants a bonus", "§aPower is selected!"),
				stack("Accessory Bag", "§7Total: §6419 Accessory Power"),
				stack("Tuning", "§7Your tuning:", "§c+34❁ Strength", "§9+7☣ Crit Chance", "")
			)
		)

		assertEquals("Sighted", MaxwellState.power)
		assertEquals(419, MaxwellState.magicalPower)
		val tunings = MaxwellState.tunings.orEmpty()
		assertEquals(listOf("Strength", "Crit Chance"), tunings.map { it.name })
		assertEquals(listOf("34", "7"), tunings.map { it.amount })
		assertEquals(listOf("§c", "§9"), tunings.map { it.color })
		assertEquals(listOf("❁", "☣"), tunings.map { it.icon })
	}

	@Test
	fun `the stats tuning page names each tuning after the stat whose stack carries it`() {
		channels.opened(
			menu(
				"Stats Tuning",
				stack("❁ Strength", "§7You have: §c1,347 §7+ §c6 ❁"),
				stack("✎ Intelligence", "§7You have: §b812 §7+ §b3 ✎")
			)
		)

		val tunings = MaxwellState.tunings.orEmpty()
		assertEquals(listOf("Strength", "Intelligence"), tunings.map { it.name })
		assertEquals(listOf("6", "3"), tunings.map { it.amount })
	}

	@Test
	fun `selecting a power in chat updates the power without any menu`() {
		channels.chatted("You selected the Sighted power for your Accessory Bag!")
		assertEquals("Sighted", MaxwellState.power)

		channels.chatted("Your selected power was set to Pretty!")
		assertEquals("Pretty", MaxwellState.power)
		assertEquals(2, updates)
	}

	@Test
	fun `an unrelated menu leaves the state alone`() {
		channels.opened(menu("Bazaar", stack("Accessory Bag", "§7Selected Power: §aSighted")))

		assertNull(MaxwellState.power)
		assertEquals(0, updates)
	}

	private fun menu(title: String, vararg stacks: ItemStack) =
		ContainerReadyEvent(Component.literal(title), 1, stacks.size, stacks.toList())

	private fun stack(name: String, vararg lore: String): ItemStack =
		ItemFixture.named(name).also { it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal))) }
}
