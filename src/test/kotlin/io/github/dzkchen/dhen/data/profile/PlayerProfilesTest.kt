package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.data.RepoBackedTest
import io.github.dzkchen.dhen.data.profile.BagFixture.VIEWED_UUID
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoState
import io.github.dzkchen.dhen.data.repo.RepoSync
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.WebResponse
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

internal class PlayerProfilesTest : RepoBackedTest() {
	private val source = FakeSource()
	private var base = PROXY
	private var nanos = 0L

	@AfterEach
	fun stopProfiles() {
		PlayerProfiles.uninstall()
	}

	@Test
	fun `asking for player data starts the item repo the levels are read out of`() {
		val root = repoRoot
		ItemRepo.install(scope, root, RepoSync(DataFixture.NEU, root, DataFixture.OFFLINE))
		install()
		assertEquals(RepoState.IDLE, ItemRepo.state)

		val requirement = PlayerProfiles.require()

		assertEquals(1, ItemRepo.required)
		assertNotEquals(RepoState.IDLE, ItemRepo.state)
		requirement.unsubscribe()
		assertEquals(0, ItemRepo.required)
	}

	@Test
	fun `a client that asks for nothing fetches nothing`() = runBlocking {
		install()

		assertNull(PlayerProfiles.profiles(VIEWED_UUID))
		assertTrue(source.requests.isEmpty())
	}

	@Test
	fun `with no proxy address the service is unavailable and asks the proxy nothing`() = runBlocking {
		base = ""
		install()
		PlayerProfiles.require()

		assertNull(PlayerProfiles.profiles(VIEWED_UUID))
		assertTrue(source.requests.isEmpty())
		assertFalse(PlayerProfiles.available)
	}

	@Test
	fun `a name resolves through the first Mojang host and is cached`() = runBlocking {
		source.bodies = mapOf(FIRST to ID_REPLY)
		install()
		PlayerProfiles.require()

		assertEquals(DASHLESS, PlayerProfiles.uuidOf(NAME))
		assertEquals(1, source.requests.size)
		assertEquals(DASHLESS, PlayerProfiles.uuidOf(NAME))
		assertEquals(1, source.requests.size)
	}

	@Test
	fun `a name resolves with no proxy address configured`() = runBlocking {
		base = ""
		source.bodies = mapOf(FIRST to ID_REPLY)
		install()
		PlayerProfiles.require()

		assertEquals(DASHLESS, PlayerProfiles.uuidOf(NAME))
	}

	@Test
	fun `a name the first Mojang host does not know falls back to the second`() = runBlocking {
		source.bodies = mapOf(SECOND to ID_REPLY)
		install()
		PlayerProfiles.require()

		assertEquals(DASHLESS, PlayerProfiles.uuidOf(NAME))
		assertEquals(listOf(FIRST, SECOND), source.requests.toList())
	}

	@Test
	fun `a rate limited Mojang endpoint is skipped for other names during its cooldown`() = runBlocking {
		source.statuses = mapOf(FIRST to 429)
		source.bodies = mapOf(SECOND to ID_REPLY, ALEX_SECOND to ALEX_ID_REPLY)
		source.onRequest = { if (source.requests.size == 1) nanos = TWO_MINUTES }
		install()
		PlayerProfiles.require()

		assertEquals(DASHLESS, PlayerProfiles.uuidOf(NAME))
		nanos = SIX_MINUTES
		assertEquals(ALEX_DASHLESS, PlayerProfiles.uuidOf(ALEX))
		assertEquals(listOf(FIRST, SECOND, ALEX_SECOND), source.requests.toList())
	}

	@Test
	fun `a Mojang not found reply stops failover and negative caches the name`() = runBlocking {
		source.statuses = mapOf(FIRST to 404)
		source.bodies = mapOf(SECOND to ID_REPLY)
		install()
		PlayerProfiles.require()

		assertNull(PlayerProfiles.uuidOf(NAME))
		assertEquals(listOf(FIRST), source.requests.toList())
		assertNull(PlayerProfiles.uuidOf(NAME))
		assertEquals(listOf(FIRST), source.requests.toList())
	}

	@Test
	fun `a name no host knows is remembered as unknown`() = runBlocking {
		install()
		PlayerProfiles.require()

		assertNull(PlayerProfiles.uuidOf(NAME))
		assertEquals(2, source.requests.size)
		assertNull(PlayerProfiles.uuidOf(NAME))
		assertEquals(2, source.requests.size)
	}

	@Test
	fun `a name that could never be a Minecraft account is never put in a URL`() = runBlocking {
		install()
		PlayerProfiles.require()

		assertNull(PlayerProfiles.uuidOf("../evil?"))
		assertTrue(source.requests.isEmpty())
	}

	@Test
	fun `the selected profile is the one the reply flags`() = runBlocking {
		source.bodies = mapOf(PROFILES to TWO_PROFILES)
		install()
		PlayerProfiles.require()

		assertEquals("Banana", PlayerProfiles.selectedProfile(VIEWED_UUID)?.get("cute_name")?.asString)
		assertEquals(1, source.requests.size)
	}

	@Test
	fun `a reply that says it failed is not cached as a success`() = runBlocking {
		source.bodies = mapOf(PROFILES to REFUSED)
		install()
		PlayerProfiles.require()

		assertNull(PlayerProfiles.profiles(VIEWED_UUID))
		assertEquals(1, source.requests.size)
		assertNull(PlayerProfiles.profiles(VIEWED_UUID))
		assertEquals(1, source.requests.size)
	}

	@Test
	fun `a reply is fetched again once its TTL has passed`() = runBlocking {
		source.bodies = mapOf(PROFILES to ONE_PROFILE)
		install()
		PlayerProfiles.require()

		assertEquals("Apple", cuteName())
		assertEquals(1, source.requests.size)
		nanos = FIVE_MINUTES
		assertEquals("Apple", cuteName())
		assertEquals(2, source.requests.size)
	}

	@Test
	fun `changing the proxy address forgets what the old one said`() = runBlocking {
		source.bodies = mapOf(PROFILES to ONE_PROFILE)
		install()
		PlayerProfiles.require()
		PlayerProfiles.profiles(VIEWED_UUID)

		assertEquals(1, source.requests.size)
		base = OTHER
		source.bodies = mapOf(OTHER_PROFILES to ONE_PROFILE)

		assertEquals("Apple", cuteName())
		assertEquals(2, source.requests.size)
	}

	@Test
	fun `a reply that lands after teardown is not remembered`() = runBlocking {
		source.bodies = mapOf(PROFILES to ONE_PROFILE)
		source.onRequest = { PlayerProfiles.uninstall() }
		install()
		PlayerProfiles.require()
		PlayerProfiles.profiles(VIEWED_UUID)
		source.onRequest = {}

		assertEquals("uuid=0, profiles=0, player=0, museum=0, garden=0, status=0, slices=0, models=0, holdings=0, gardens=0", PlayerProfiles.cacheSummary())
	}

	@Test
	fun `the raw replies cache holds no more profiles than the decoded ones do`() = runBlocking {
		install()
		PlayerProfiles.require()

		for (uuid in TWENTY_UUIDS) PlayerProfiles.profiles(uuid)

		assertEquals(TWENTY_UUIDS.size, source.requests.size)
		assertTrue(PlayerProfiles.cacheSummary().contains("profiles=16"), PlayerProfiles.cacheSummary())
	}

	@Test
	fun `a reply produced before the proxy address changed is never served for the new one`() = runBlocking {
		source.bodies = mapOf(PROFILES to ONE_PROFILE, OTHER_PROFILES to OTHER_PROFILE, OTHER_SECOND to ONE_PROFILE)
		install()
		PlayerProfiles.require()
		source.onRequest = {
			source.onRequest = {}
			base = OTHER
			runBlocking { PlayerProfiles.profiles(SECOND_UUID) }
		}

		PlayerProfiles.profiles(VIEWED_UUID)

		assertTrue(PlayerProfiles.cacheSummary().contains("profiles=1"), PlayerProfiles.cacheSummary())
		assertEquals("Cherry", cuteName())
	}

	@Test
	fun `slice decodes the fetched reply, and does so with no item repo behind it`() = runBlocking {
		source.bodies = mapOf(PROFILES to SLICE_PROFILE)
		install()
		PlayerProfiles.require()

		assertEquals(RepoState.IDLE, ItemRepo.state)
		val slice = PlayerProfiles.slice(VIEWED_UUID)!!
		assertEquals("p1", slice.profileId)
		assertEquals("Strawberry", slice.cuteName)
	}

	@Test
	fun `a second call to slice makes no further request`() = runBlocking {
		source.bodies = mapOf(PROFILES to SLICE_PROFILE)
		install()
		PlayerProfiles.require()
		PlayerProfiles.slice(VIEWED_UUID)
		assertEquals(1, source.requests.size)
		PlayerProfiles.slice(VIEWED_UUID)
		assertEquals(1, source.requests.size)
	}

	@Test
	fun `profile decodes the fetched reply and keeps it once the item repo is ready`() = runBlocking {
		source.bodies = mapOf(PROFILES to SLICE_PROFILE)
		DataFixture.installRepo(scope, repoRoot, items = mapOf("AOTE" to DataFixture.ANY_ITEM))
		install()
		PlayerProfiles.require()

		assertEquals("Strawberry", PlayerProfiles.profile(VIEWED_UUID)?.slice?.cuteName)
		assertTrue(PlayerProfiles.cacheSummary().contains("models=1"))
	}

	@Test
	fun `a profile asked for before the item repo is ready reads as not ready, rather than as a player with no levels`() = runBlocking {
		source.bodies = mapOf(PROFILES to SLICE_PROFILE)
		install()
		PlayerProfiles.require()

		assertEquals(RepoState.IDLE, ItemRepo.state)
		assertNull(PlayerProfiles.profile(VIEWED_UUID))

		assertTrue(PlayerProfiles.cacheSummary().contains("models=0"))
	}

	@Test
	fun `a profile asked for while the item repo is unavailable reads as not ready, and reads for real once it recovers`() =
		runBlocking {
			source.bodies = mapOf(PROFILES to SLICE_PROFILE)
			DataFixture.installRepo(scope, repoRoot)
			install()
			PlayerProfiles.require()

			assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
			assertNull(PlayerProfiles.profile(VIEWED_UUID))
			assertTrue(PlayerProfiles.cacheSummary().contains("models=0"))

			DataFixture.installRepo(scope, repoRoot, items = mapOf("AOTE" to DataFixture.ANY_ITEM))

			assertEquals("Strawberry", PlayerProfiles.profile(VIEWED_UUID)?.slice?.cuteName)
		}

	@Test
	fun `a decoded profile hands its slice to the next caller rather than decoding a second one`() = runBlocking {
		source.bodies = mapOf(PROFILES to SLICE_PROFILE)
		DataFixture.installRepo(scope, repoRoot, items = mapOf("AOTE" to DataFixture.ANY_ITEM))
		install()
		PlayerProfiles.require()

		val profile = PlayerProfiles.profile(VIEWED_UUID)!!

		assertEquals("Strawberry", profile.slice.cuteName)
		assertSame(profile.slice, PlayerProfiles.slice(VIEWED_UUID))
	}

	@Test
	fun `no more than five requests are ever in flight at once`() {
		source.dwellMillis = 20
		install()
		PlayerProfiles.require()
		runBlocking {
			ELEVEN_UUIDS.map { uuid -> async(Dispatchers.IO) { PlayerProfiles.profiles(uuid) } }.awaitAll()
		}

		assertEquals(ELEVEN_UUIDS.size, source.requests.size)
		assertTrue(source.peak in 1..5, "peaked at ${source.peak} concurrent requests")
		assertTrue(PlayerProfiles.peakInFlight in 1..5)
	}

	private suspend fun cuteName(): String? =
		PlayerProfiles.profiles(VIEWED_UUID)?.getAsJsonArray("profiles")?.get(0)?.asJsonObject?.get("cute_name")?.asString

	private fun install() =
		PlayerProfiles.install(scope, Dispatchers.Unconfined, source, NanoClock { nanos }) { base }

	private class FakeSource : WebSource {
		val requests: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
		private val active = AtomicInteger()
		private val highest = AtomicInteger()

		var bodies: Map<String, String> = emptyMap()
		var statuses: Map<String, Int> = emptyMap()
		var dwellMillis = 0L
		var onRequest: () -> Unit = {}

		val peak: Int get() = highest.get()

		override fun text(url: String): String? = response(url).body

		override fun response(url: String): WebResponse {
			val now = active.incrementAndGet()
			highest.updateAndGet { seen -> maxOf(seen, now) }
			requests += url
			onRequest()
			if (dwellMillis > 0) Thread.sleep(dwellMillis)
			active.decrementAndGet()
			val body = bodies[url]
			return WebResponse(body, statuses[url] ?: body?.let { 200 })
		}
	}

	private companion object {
		private const val NAME = "Steve"
		private const val ALEX = "Alex"
		private const val DASHED = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
		private const val DASHLESS = "069a79f444e94726a5befca90e38aaf5"
		private const val ALEX_DASHED = "ec561538-f3fd-461d-aff5-086b22154bce"
		private const val ALEX_DASHLESS = "ec561538f3fd461daff5086b22154bce"
		private const val PROXY = "https://proxy.example.com"
		private const val OTHER = "https://other.example.com"
		private const val TWO_MINUTES = 120_000_000_000L
		private const val FIVE_MINUTES = 300_000_000_001L
		private const val SIX_MINUTES = 360_000_000_000L

		private const val FIRST = "https://api.minecraftservices.com/minecraft/profile/lookup/name/$NAME"
		private const val SECOND = "https://api.mojang.com/users/profiles/minecraft/$NAME"
		private const val ALEX_SECOND = "https://api.mojang.com/users/profiles/minecraft/$ALEX"
		private const val PROFILES = "$PROXY/v2/skyblock/profiles?uuid=$VIEWED_UUID"
		private const val OTHER_PROFILES = "$OTHER/v2/skyblock/profiles?uuid=$VIEWED_UUID"
		private const val SECOND_UUID = "123e4567-e89b-12d3-a456-426614174001"
		private const val OTHER_SECOND = "$OTHER/v2/skyblock/profiles?uuid=$SECOND_UUID"

		private const val ID_REPLY = """{"id":"$DASHED","name":"$NAME"}"""
		private const val ALEX_ID_REPLY = """{"id":"$ALEX_DASHED","name":"$ALEX"}"""
		private const val REFUSED = """{"success":false,"cause":"Malformed UUID"}"""
		private const val ONE_PROFILE =
			"""{"success":true,"profiles":[{"profile_id":"1","cute_name":"Apple","selected":true}]}"""
		private const val OTHER_PROFILE =
			"""{"success":true,"profiles":[{"profile_id":"9","cute_name":"Cherry","selected":true}]}"""
		private const val TWO_PROFILES =
			"""{"success":true,"profiles":[{"profile_id":"1","cute_name":"Apple","selected":false},""" +
				"""{"profile_id":"2","cute_name":"Banana","selected":true}]}"""
		private const val SLICE_PROFILE =
			"""{"success":true,"profiles":[{"profile_id":"p1","cute_name":"Strawberry","selected":true,"members":{"${BagFixture.VIEWED}":{}}}]}"""

		private val TWENTY_UUIDS = "0123456789abcdefABCD".map { last -> VIEWED_UUID.dropLast(1) + last }
		private val ELEVEN_UUIDS = TWENTY_UUIDS.take(11)
	}
}
