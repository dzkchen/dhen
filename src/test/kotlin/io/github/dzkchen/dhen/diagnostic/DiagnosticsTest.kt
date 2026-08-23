package io.github.dzkchen.dhen.diagnostic

import io.github.dzkchen.dhen.data.profile.PlayerProfiles
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiagnosticsTest {
	private val scope = CoroutineScope(Dispatchers.Unconfined)
	private val source = FakeSource()
	private val diagnostics = Diagnostics(ModuleManager()) { null }

	@AfterEach
	fun uninstall() = PlayerProfiles.uninstall()

	@Test
	fun `a lookup with no profile service running says so instead of falling silent`() {
		assertEquals(
			listOf("Dhen's player-profile service is not running, so it cannot look anybody up."),
			lookup(1)
		)
	}

	@Test
	fun `a name nobody knows is reported rather than left hanging`() {
		install()

		assertEquals(
			listOf("Dhen could not look '$NAME' up — either no such account exists, or Mojang did not answer."),
			lookup(1)
		)
	}

	@Test
	fun `a found player is reported profile by profile`() {
		source.bodies = mapOf(MOJANG to ID_REPLY, PROFILES to ONE_PROFILE, STATUS to ONLINE)
		install()

		assertEquals(
			listOf(
				"$NAME is $DASHLESS.",
				"$NAME has 1 SkyBlock profiles; the selected one is 'Strawberry'.",
				"That profile has no dungeon data.",
				"Dhen cannot read the talisman bag, so it is assuming a magical power of 0.",
				"The inventory API is off for that profile.",
				"Right now they are online in SKYBLOCK (ironman, Private Island)."
			),
			lookup(6)
		)
	}

	private fun lookup(lineCount: Int): List<String> {
		val lines = Collections.synchronizedList(mutableListOf<String>())
		val reported = CountDownLatch(lineCount)
		diagnostics.profileLookup(NAME) { line ->
			lines += line
			reported.countDown()
		}
		assertTrue(reported.await(PATIENCE_SECONDS, TimeUnit.SECONDS), "the lookup reported ${lines.size} lines")
		return lines.toList()
	}

	private fun install() =
		PlayerProfiles.install(scope, Dispatchers.Unconfined, source, NanoClock { 0L }) { PROXY }

	private class FakeSource : WebSource {
		var bodies: Map<String, String> = emptyMap()

		override fun text(url: String): String? = bodies[url]
	}

	private companion object {
		private const val PATIENCE_SECONDS = 5L
		private const val NAME = "Steve"
		private const val DASHED = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
		private const val DASHLESS = "069a79f444e94726a5befca90e38aaf5"
		private const val PROXY = "https://proxy.example.com"

		private const val MOJANG = "https://api.minecraftservices.com/minecraft/profile/lookup/name/$NAME"
		private const val PROFILES = "$PROXY/v2/skyblock/profiles?uuid=$DASHLESS"
		private const val STATUS = "$PROXY/v2/status?uuid=$DASHLESS"

		private const val ID_REPLY = """{"id":"$DASHED","name":"$NAME"}"""
		private const val ONE_PROFILE =
			"""{"success":true,"profiles":[{"profile_id":"p1","cute_name":"Strawberry","selected":true,""" +
				""""members":{"$DASHLESS":{}}}]}"""
		private const val ONLINE =
			"""{"success":true,"session":{"online":true,"gameType":"SKYBLOCK","mode":"ironman","map":"Private Island"}}"""
	}
}
