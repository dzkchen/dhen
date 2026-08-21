package io.github.dzkchen.dhen.data.profile

import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class PlayerProfilesTest {
	private val scope = CoroutineScope(Dispatchers.Unconfined)
	private val source = FakeSource()
	private var base = PROXY
	private var nanos = 0L

	@AfterEach
	fun uninstall() {
		PlayerProfiles.uninstall()
	}

	@Test
	fun `a client that asks for nothing fetches nothing`() = runBlocking {
		install()

		assertNull(PlayerProfiles.profiles(UUID))
		assertTrue(source.requests.isEmpty())
	}

	@Test
	fun `with no proxy address the service is unavailable and asks the proxy nothing`() = runBlocking {
		base = ""
		install()
		PlayerProfiles.require()

		assertNull(PlayerProfiles.profiles(UUID))
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

		assertEquals("Banana", PlayerProfiles.selectedProfile(UUID)?.get("cute_name")?.asString)
		assertEquals(1, source.requests.size)
	}

	@Test
	fun `a reply that says it failed is not cached as a success`() = runBlocking {
		source.bodies = mapOf(PROFILES to REFUSED)
		install()
		PlayerProfiles.require()

		assertNull(PlayerProfiles.profiles(UUID))
		assertEquals(1, source.requests.size)
		assertNull(PlayerProfiles.profiles(UUID))
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
		PlayerProfiles.profiles(UUID)

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
		PlayerProfiles.profiles(UUID)
		source.onRequest = {}

		assertEquals("uuid=0, profiles=0, player=0, museum=0, garden=0, status=0, slices=0, holdings=0", PlayerProfiles.cacheSummary())
	}

	@Test
	fun `slice decodes the fetched reply`() = runBlocking {
		source.bodies = mapOf(PROFILES to SLICE_PROFILE)
		install()
		PlayerProfiles.require()
		val slice = PlayerProfiles.slice(UUID)!!
		assertEquals("p1", slice.profileId)
		assertEquals("Strawberry", slice.cuteName)
	}

	@Test
	fun `a second call to slice makes no further request`() = runBlocking {
		source.bodies = mapOf(PROFILES to SLICE_PROFILE)
		install()
		PlayerProfiles.require()
		PlayerProfiles.slice(UUID)
		assertEquals(1, source.requests.size)
		PlayerProfiles.slice(UUID)
		assertEquals(1, source.requests.size)
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
		PlayerProfiles.profiles(UUID)?.getAsJsonArray("profiles")?.get(0)?.asJsonObject?.get("cute_name")?.asString

	private fun install() =
		PlayerProfiles.install(scope, Dispatchers.Unconfined, source, NanoClock { nanos }) { base }

	private class FakeSource : WebSource {
		val requests: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
		private val active = AtomicInteger()
		private val highest = AtomicInteger()

		var bodies: Map<String, String> = emptyMap()
		var dwellMillis = 0L
		var onRequest: () -> Unit = {}

		val peak: Int get() = highest.get()

		override fun text(url: String): String? {
			val now = active.incrementAndGet()
			highest.updateAndGet { seen -> maxOf(seen, now) }
			requests += url
			onRequest()
			if (dwellMillis > 0) Thread.sleep(dwellMillis)
			active.decrementAndGet()
			return bodies[url]
		}
	}

	private companion object {
		private const val NAME = "Steve"
		private const val UUID = "123e4567-e89b-12d3-a456-426614174000"
		private const val DASHED = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
		private const val DASHLESS = "069a79f444e94726a5befca90e38aaf5"
		private const val PROXY = "https://proxy.example.com"
		private const val OTHER = "https://other.example.com"
		private const val FIVE_MINUTES = 300_000_000_001L

		private const val FIRST = "https://api.minecraftservices.com/minecraft/profile/lookup/name/$NAME"
		private const val SECOND = "https://api.mojang.com/users/profiles/minecraft/$NAME"
		private const val PROFILES = "$PROXY/v2/skyblock/profiles?uuid=$UUID"
		private const val OTHER_PROFILES = "$OTHER/v2/skyblock/profiles?uuid=$UUID"

		private const val ID_REPLY = """{"id":"$DASHED","name":"$NAME"}"""
		private const val REFUSED = """{"success":false,"cause":"Malformed UUID"}"""
		private const val ONE_PROFILE =
			"""{"success":true,"profiles":[{"profile_id":"1","cute_name":"Apple","selected":true}]}"""
		private const val TWO_PROFILES =
			"""{"success":true,"profiles":[{"profile_id":"1","cute_name":"Apple","selected":false},""" +
				"""{"profile_id":"2","cute_name":"Banana","selected":true}]}"""
		private const val SLICE_PROFILE =
			"""{"success":true,"profiles":[{"profile_id":"p1","cute_name":"Strawberry","selected":true,"members":{"123e4567e89b12d3a456426614174000":{}}}]}"""

		private val ELEVEN_UUIDS = "0123456789a".map { last -> UUID.dropLast(1) + last }
	}
}
