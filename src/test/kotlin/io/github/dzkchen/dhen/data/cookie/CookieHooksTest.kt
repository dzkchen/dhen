package io.github.dzkchen.dhen.data.cookie

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.CookieUpdateEvent
import io.github.dzkchen.dhen.event.EventBus
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CookieHooksTest {
	private val now = 1_000_000L
	private lateinit var channels: CookieHooks.Channels
	private var updates = 0

	@BeforeAll
	fun bootstrap() = ItemFixture.bootstrap()

	@BeforeEach
	fun prepare() {
		CookieState.reset()
		val bus = EventBus()
		channels = CookieHooks.Channels(bus, { now }, inSkyBlock = { true })
		bus.subscribe<CookieUpdateEvent> { updates++ }
		updates = 0
	}

	@AfterEach
	fun clear() {
		CookieState.reset()
	}

	@Test
	fun `the SkyBlock menu cookie turns its remaining duration into an expiry`() {
		channels.opened(
			menu("SkyBlock Menu", stack("Booster Cookie", " §7Duration: §a3d 17h 5m 36s"))
		)

		assertEquals(now + 3 * DAY + 17 * HOUR + 5 * MINUTE + 36 * SECOND, CookieState.expiry)
		assertEquals(1, updates)
	}

	@Test
	fun `a cookie the menu reports inactive expires immediately`() {
		channels.opened(menu("SkyBlock Menu", stack("Booster Cookie", " §7Status: §cNot active!")))

		assertEquals(CookieState.EXPIRED, CookieState.expiry)
	}

	@Test
	fun `a SkyBlock menu with no cookie at all expires the buff`() {
		channels.opened(menu("SkyBlock Menu", stack("Your Bags", "§7Nothing here")))

		assertEquals(CookieState.EXPIRED, CookieState.expiry)
	}

	@Test
	fun `the booster cookie shop reads the same duration line`() {
		channels.opened(menu("Community Shop", stack("Booster Cookie", " §7Duration: §a1h")))

		assertEquals(now + HOUR, CookieState.expiry)
	}

	@Test
	fun `eating a cookie adds four days to whatever was left`() {
		CookieState.expires(now + HOUR)

		channels.chatted("You consumed a Booster Cookie! Yummy!")

		assertEquals(now + HOUR + 4 * DAY, CookieState.expiry)
		assertEquals(1, updates)
	}

	@Test
	fun `eating a cookie with none active counts four days from now`() {
		channels.chatted("You consumed a Booster Cookie!")

		assertEquals(now + 4 * DAY, CookieState.expiry)
	}

	@Test
	fun `an unrelated menu leaves the buff alone`() {
		channels.opened(menu("Bazaar", stack("Booster Cookie", " §7Duration: §a1h")))

		assertEquals(CookieState.UNKNOWN, CookieState.expiry)
		assertEquals(0, updates)
	}

	private fun menu(title: String, vararg stacks: ItemStack) =
		ContainerReadyEvent(Component.literal(title), 1, stacks.size, stacks.toList())

	private fun stack(name: String, vararg lore: String): ItemStack =
		ItemFixture.named(name).also { it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal))) }

	private companion object {
		const val SECOND = 1_000L
		const val MINUTE = 60 * SECOND
		const val HOUR = 60 * MINUTE
		const val DAY = 24 * HOUR
	}
}
