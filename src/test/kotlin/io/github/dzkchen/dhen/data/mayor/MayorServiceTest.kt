package io.github.dzkchen.dhen.data.mayor

import io.github.dzkchen.dhen.data.item.ItemFixture
import io.github.dzkchen.dhen.event.ChatReceiveEvent
import io.github.dzkchen.dhen.event.ContainerReadyEvent
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.MayorChangeEvent
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class MayorServiceTest {
	private val scope = CoroutineScope(Dispatchers.Unconfined)
	private val bus = EventBus()
	private val source = FakeSource()
	private var onHypixel = true
	private var inSkyBlock = true
	private var millis = SkyBlockCalendar.millisAt(509, 6, 1)

	@AfterEach
	fun uninstall() {
		MayorService.uninstall()
	}

	@Test
	fun `a client that never asks for the mayor fetches nothing`() {
		install()

		assertTrue(source.requests.isEmpty())
		assertNull(MayorService.mayor)
	}

	@Test
	fun `the first module that needs the mayor gets the mayor, the perks and the minister`() {
		install()

		MayorService.require()

		assertEquals(1, source.requests.size)
		assertEquals("Diana", MayorService.mayor?.name)
		assertEquals(setOf("Mythological Ritual", "Pet XP Buff"), MayorService.mayor?.perks)
		assertEquals("Cole", MayorService.minister)
		assertEquals("Mining XP Buff", MayorService.ministerPerk)
		assertEquals(509, MayorService.electedYear)
		assertEquals(SkyBlockCalendar.millisAt(510, 3, 27), MayorService.nextElectionAt)
	}

	@Test
	fun `a client that is not on hypixel asks the api for nothing`() {
		onHypixel = false
		install()

		MayorService.require()

		assertTrue(source.requests.isEmpty())
	}

	@Test
	fun `a perk is active whether the mayor or the minister carries it`() {
		install()
		MayorService.require()

		assertTrue(MayorService.isPerkActive("Mythological Ritual"))
		assertTrue(MayorService.isPerkActive("Mining XP Buff"))
		assertFalse(MayorService.isPerkActive("Slayer XP Buff"))
	}

	@Test
	fun `a reply that predates the current election is refused rather than believed`() {
		source.lastUpdated = SkyBlockCalendar.millisAt(508, 4, 1)
		install()

		MayorService.require()

		assertNull(MayorService.mayor)
		assertEquals(1, MayorService.failedRefreshes)
	}

	@Test
	fun `a mayor the election has since unseated reads back as unknown, not as the old mayor`() {
		install()
		MayorService.require()

		millis = SkyBlockCalendar.millisAt(510, 4, 1)

		assertNull(MayorService.mayor)
		assertFalse(MayorService.isPerkActive("Mythological Ritual"))
	}

	@Test
	fun `a reply whose perk list is not what the api promised is refused whole`() {
		source.body = ELECTION.format(SkyBlockCalendar.millisAt(509, 4, 1), "Diana").replace("{\"name\": \"Pet XP Buff\"}", "\"Pet XP Buff\"")
		install()

		MayorService.require()

		assertNull(MayorService.mayor)
		assertEquals(1, MayorService.failedRefreshes)
	}

	@Test
	fun `a source that answers nothing leaves the mayor unknown rather than broken`() {
		source.reachable = false
		install()

		MayorService.require()

		assertNull(MayorService.mayor)
		assertFalse(MayorService.isPerkActive("Mythological Ritual"))
		assertEquals(1, MayorService.failedRefreshes)
	}

	@Test
	fun `a new mayor tells its consumers`() {
		install()
		var landed: MayorChangeEvent? = null
		bus.subscribe<MayorChangeEvent> { landed = it }

		MayorService.require()

		assertEquals("Diana", landed?.mayor)
		assertNull(landed?.previous)
	}

	@Test
	fun `the election room closing forgets the mayor and tells its consumers`() {
		install()
		MayorService.require()
		var landed: MayorChangeEvent? = null
		bus.subscribe<MayorChangeEvent> { landed = it }

		bus.type<ChatReceiveEvent>().dispatch(chat("The election room is now closed. Clerk Seraphine is doing a final count of the votes..."))

		assertNull(MayorService.mayor)
		assertNull(landed?.mayor)
		assertEquals("Diana", landed?.previous)
	}

	@Test
	fun `an ordinary chat line leaves the mayor alone`() {
		install()
		MayorService.require()

		bus.type<ChatReceiveEvent>().dispatch(chat("Party > dzkchen: the election room is fine"))

		assertEquals("Diana", MayorService.mayor?.name)
	}

	@Test
	fun `the calendar names the perkpocalypse perk while jerry is mayor`() {
		source.mayor = "Jerry"
		install()
		MayorService.require()

		openCalendar(jerryHead(BORROWED_PERK))

		assertEquals(BORROWED_PERK, MayorService.perkpocalypsePerk)
		assertTrue(MayorService.isPerkActive(BORROWED_PERK))
	}

	@Test
	fun `a perkpocalypse perk expires when its six hours are up`() {
		source.mayor = "Jerry"
		install()
		MayorService.require()
		openCalendar(jerryHead(BORROWED_PERK))

		millis += 6.hours.inWholeMilliseconds

		assertNull(MayorService.perkpocalypsePerk)
		assertFalse(MayorService.isPerkActive(BORROWED_PERK))
	}

	@Test
	fun `the calendar is not read while somebody else is mayor`() {
		install()
		MayorService.require()

		openCalendar(jerryHead(BORROWED_PERK))

		assertNull(MayorService.perkpocalypsePerk)
	}

	@Test
	fun `a second module needing the mayor does not refetch`() {
		install()

		MayorService.require()
		MayorService.require()

		assertEquals(1, source.requests.size)
		assertEquals(2, MayorService.required)
	}

	@Test
	fun `releasing a requirement drops it once however often it is released`() {
		install()
		val handle = MayorService.require()

		handle.unsubscribe()
		handle.unsubscribe()

		assertEquals(0, MayorService.required)
	}

	@Test
	fun `a requirement released after the service was torn down cannot drive the count negative`() {
		install()
		val handle = MayorService.require()

		MayorService.uninstall()
		install()
		handle.unsubscribe()

		assertEquals(0, MayorService.required)
	}

	@Test
	fun `needing the mayor before the service is installed is a no-op`() {
		MayorService.require()

		assertEquals(0, MayorService.required)
		assertTrue(source.requests.isEmpty())
	}

	@Test
	fun `tearing the service down stops it fetching, and a fresh one fetches again`() {
		install()
		MayorService.require()
		MayorService.uninstall()

		MayorService.require()

		assertEquals(1, source.requests.size)
		assertNull(MayorService.mayor)

		install()
		MayorService.require()

		assertEquals(2, source.requests.size)
		assertEquals("Diana", MayorService.mayor?.name)
	}

	@Test
	fun `asking for the mayor again in the same minute reuses what was just read`() {
		install()

		MayorService.require().unsubscribe()
		MayorService.require().unsubscribe()
		MayorService.require()

		assertEquals(1, source.requests.size)
	}

	@Test
	fun `releasing the last requirement stops the poll, and needing the mayor again starts it`() {
		install()
		val first = MayorService.require()
		val second = MayorService.require()
		assertTrue(MayorService.polling)

		first.unsubscribe()
		assertTrue(MayorService.polling)

		second.unsubscribe()
		assertFalse(MayorService.polling)

		MayorService.require()

		assertTrue(MayorService.polling)
	}

	private fun install() {
		MayorService.install(scope, bus, Dispatchers.Unconfined, source, { millis }, { onHypixel }) { inSkyBlock }
	}

	private fun chat(line: String): ChatReceiveEvent =
		ChatReceiveEvent().also { it.text = Component.literal(line) }

	private fun openCalendar(vararg stacks: ItemStack) {
		bus.type<ContainerReadyEvent>()
			.dispatch(ContainerReadyEvent(Component.literal("Late Spring, Year 509"), 1, stacks.size, stacks.toList()))
	}

	private fun jerryHead(perk: String): ItemStack =
		ItemFixture.named(PERKPOCALYPSE_HEAD).also {
			it.set(
				DataComponents.LORE,
				ItemLore(listOf("Perkpocalypse Perks:", "", perk).map(Component::literal))
			)
		}

	private class FakeSource : WebSource {
		val requests = mutableListOf<String>()
		var reachable = true
		var mayor = "Diana"
		var lastUpdated = SkyBlockCalendar.millisAt(509, 4, 1)
		var body: String? = null

		override fun text(url: String): String? {
			requests += url
			if (!reachable) return null
			return body ?: ELECTION.format(lastUpdated, mayor)
		}
	}

	private companion object {
		private const val PERKPOCALYPSE_HEAD = "Mayor Jerry"
		private const val BORROWED_PERK = "Slayer XP Buff"

		private val ELECTION = """
			{
			  "success": true,
			  "lastUpdated": %d,
			  "mayor": {
			    "key": "diana",
			    "name": "%s",
			    "perks": [{"name": "Mythological Ritual"}, {"name": "Pet XP Buff"}],
			    "minister": {"key": "cole", "name": "Cole", "perk": {"name": "Mining XP Buff"}},
			    "election": {"year": 508, "candidates": []}
			  },
			  "current": {"year": 509, "candidates": []}
			}
		""".trimIndent()

		@JvmStatic
		@BeforeAll
		fun bootstrap() {
			ItemFixture.bootstrap()
		}
	}
}
