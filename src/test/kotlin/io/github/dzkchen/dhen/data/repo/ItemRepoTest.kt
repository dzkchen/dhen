package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.util.NanoClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ItemRepoTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)
	private val transport = FakeTransport()

	private var nanos = 0L

	@AfterEach
	fun uninstall() {
		ItemRepo.uninstall()
	}

	@Test
	fun `a client that never asks for item data downloads nothing`() {
		install()

		assertEquals(RepoState.IDLE, ItemRepo.state)
		assertEquals(0, transport.downloads)
		assertEquals(0, ItemRepo.size)
		assertNull(ItemRepo.item("ASPECT_OF_THE_END"))
	}

	@Test
	fun `the first module that needs item data starts the download`() {
		install()

		ItemRepo.require()

		assertEquals(RepoState.READY, ItemRepo.state)
		assertEquals(1, transport.downloads)
		assertEquals("§5Aspect of the End", ItemRepo.item("ASPECT_OF_THE_END")?.displayName)
		assertEquals("abc123", ItemRepo.commit)
	}

	@Test
	fun `a second module needing item data does not download again`() {
		install()

		ItemRepo.require()
		ItemRepo.require()

		assertEquals(1, transport.downloads)
		assertEquals(2, ItemRepo.required)
	}

	@Test
	fun `releasing a requirement drops it once however often it is released`() {
		install()
		val handle = ItemRepo.require()

		handle.unsubscribe()
		handle.unsubscribe()

		assertEquals(0, ItemRepo.required)
	}

	@Test
	fun `a requirement released after the repo was torn down cannot drive the count negative`() {
		install()
		val handle = ItemRepo.require()

		ItemRepo.uninstall()
		install()
		handle.unsubscribe()

		assertEquals(0, ItemRepo.required)
	}

	@Test
	fun `an unreachable repo still reads the copy left on disk`() {
		Files.createDirectories(home.resolve("repo/items"))
		Files.writeString(
			home.resolve("repo/items/ASPECT_OF_THE_END.json"),
			"{\"internalname\":\"ASPECT_OF_THE_END\",\"displayname\":\"§5Aspect of the End\"}"
		)
		transport.reachable = false
		install()

		ItemRepo.require()

		assertEquals(RepoState.READY, ItemRepo.state)
		assertEquals(1, ItemRepo.size)
	}

	@Test
	fun `an unreachable repo with nothing on disk is unavailable rather than broken`() {
		transport.reachable = false
		install()

		ItemRepo.require()

		assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
		assertEquals(0, ItemRepo.size)
	}

	@Test
	fun `a repo that could not be reached retries on its own once the source comes back`() {
		transport.reachable = false
		installRetryingEvery(300.milliseconds)
		ItemRepo.require()

		assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
		assertEquals(0, transport.downloads)

		transport.reachable = true
		waitUntil("the repo reloads itself") { ItemRepo.state == RepoState.READY }

		assertEquals(1, transport.downloads)
		assertEquals("§5Aspect of the End", ItemRepo.item("ASPECT_OF_THE_END")?.displayName)
	}

	@Test
	fun `a repo nothing needs any more is not retried`() {
		transport.reachable = false
		installRetryingEvery(200.milliseconds)
		val handle = ItemRepo.require()

		handle.unsubscribe()
		transport.reachable = true
		Thread.sleep(600)

		assertFalse(ItemRepo.retrying)
		assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
		assertEquals(1, transport.probes)
	}

	@Test
	fun `a repo torn down inside the retry window leaves nothing running`() {
		transport.reachable = false
		installRetryingEvery(200.milliseconds)
		ItemRepo.require()
		assertTrue(ItemRepo.retrying)

		ItemRepo.uninstall()
		transport.reachable = true
		Thread.sleep(600)

		assertFalse(ItemRepo.retrying)
		assertEquals(RepoState.IDLE, ItemRepo.state)
		assertEquals(1, transport.probes)
	}

	@Test
	fun `a repo that stays unreachable gives up after six attempts`() {
		transport.reachable = false
		installRetryingEvery(20.milliseconds)
		ItemRepo.require()

		waitUntil("the repo gives up") { !ItemRepo.retrying }
		Thread.sleep(100)

		assertEquals(6, transport.probes)
		assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
	}

	@Test
	fun `releasing one requirement does not start the tries over for the ones still held`() {
		transport.reachable = false
		installRetryingEvery(200.milliseconds)
		val first = ItemRepo.require()
		ItemRepo.require()
		waitUntil("the repo gives up") { !ItemRepo.retrying }
		assertEquals(6, transport.probes)

		first.unsubscribe()
		Thread.sleep(600)

		assertFalse(ItemRepo.retrying)
		assertEquals(6, transport.probes)
	}

	@Test
	fun `asking again after the repo gave up buys a fresh set of tries`() {
		transport.reachable = false
		installRetryingEvery(200.milliseconds)
		ItemRepo.require()
		waitUntil("the repo gives up") { !ItemRepo.retrying }
		assertEquals(6, transport.probes)

		transport.reachable = true
		ItemRepo.require()

		waitUntil("the repo reloads itself") { ItemRepo.state == RepoState.READY }
	}

	@Test
	fun `a repo that loaded is never asked again however long the session runs`() {
		install()
		ItemRepo.require()

		nanos = 1.hours.inWholeNanoseconds
		ItemRepo.require()

		assertEquals(1, transport.downloads)
	}

	@Test
	fun `a download that finishes after the repo was torn down leaves it empty`() {
		install()
		transport.onDownload = { ItemRepo.uninstall() }

		ItemRepo.require()

		assertEquals(RepoState.IDLE, ItemRepo.state)
		assertEquals(0, ItemRepo.size)
		assertNull(ItemRepo.commit)
	}

	@Test
	fun `a download that finishes after the repo was reinstalled does not fill the new one`() {
		install()
		transport.onDownload = {
			transport.onDownload = {}
			install()
		}

		ItemRepo.require()

		assertEquals(RepoState.IDLE, ItemRepo.state)
		assertEquals(0, ItemRepo.size)
	}

	@Test
	fun `a load that lands after a reinstall does not lock the new repo out`() {
		install()
		transport.onDownload = {
			transport.onDownload = {}
			install()
		}
		ItemRepo.require()
		assertEquals(RepoState.IDLE, ItemRepo.state)

		ItemRepo.require()

		assertEquals(RepoState.READY, ItemRepo.state)
		assertEquals(1, transport.downloads)
	}

	@Test
	fun `a load that throws keeps the constants and commit the last read produced`() {
		Files.createDirectories(home.resolve("repo/constants"))
		Files.writeString(home.resolve("repo/constants/reforgestones.json"), ConstantsFixture.REFORGE_STONES)
		Files.writeString(home.resolve("repo.commit"), "abc123")
		transport.reachable = false
		install()
		val held = ItemRepo.require()
		assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
		assertEquals(3, ItemRepo.constants.reforgeStoneCount)
		assertEquals("abc123", ItemRepo.commit)

		held.unsubscribe()
		transport.broken = true
		nanos = 5.minutes.inWholeNanoseconds
		ItemRepo.require()

		assertEquals(RepoState.UNAVAILABLE, ItemRepo.state)
		assertEquals(3, ItemRepo.constants.reforgeStoneCount)
		assertEquals("abc123", ItemRepo.commit)
	}

	@Test
	fun `requiring item data before the repo is installed is a no-op`() {
		ItemRepo.require()

		assertEquals(RepoState.IDLE, ItemRepo.state)
		assertEquals(0, ItemRepo.required)
	}

	private fun install() {
		val root = home.resolve("repo")
		ItemRepo.install(scope, root, RepoSync(DataFixture.NEU, root, transport), clock = { nanos })
	}

	private fun installRetryingEvery(window: Duration) {
		val root = home.resolve("repo")
		ItemRepo.install(scope, root, RepoSync(DataFixture.NEU, root, transport), NanoClock.SYSTEM, window)
	}

	private fun waitUntil(expected: String, reached: () -> Boolean) {
		val deadline = System.nanoTime() + 10.seconds.inWholeNanoseconds
		while (System.nanoTime() < deadline) {
			if (reached()) return
			Thread.sleep(5)
		}
		fail<Unit>("Waited ten seconds for $expected")
	}

	private class FakeTransport : RepoTransport {
		@Volatile
		var reachable = true

		@Volatile
		var broken = false

		@Volatile
		var probes = 0

		@Volatile
		var downloads = 0

		var onDownload: () -> Unit = {}

		override fun text(url: String): String? {
			probes++
			if (broken) throw IllegalStateException("the repo host went away mid-read")
			return if (reachable) "{\"sha\":\"abc123\"}" else null
		}

		override fun download(url: String, destination: Path): Boolean {
			downloads++
			onDownload()
			if (!reachable) return false
			destination.parent?.let(Files::createDirectories)
			Files.write(destination, ARCHIVE)
			return true
		}
	}

	private companion object {
		private val ARCHIVE: ByteArray = ByteArrayOutputStream().also { bytes ->
			ZipOutputStream(bytes).use { zip ->
				zip.putNextEntry(ZipEntry("NotEnoughUpdates-REPO-abc123/items/ASPECT_OF_THE_END.json"))
				zip.write("{\"internalname\":\"ASPECT_OF_THE_END\",\"displayname\":\"§5Aspect of the End\"}".toByteArray())
				zip.closeEntry()
			}
		}.toByteArray()
	}
}
