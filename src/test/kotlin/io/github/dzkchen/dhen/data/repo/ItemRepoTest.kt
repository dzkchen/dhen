package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.DataFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ItemRepoTest {
	@TempDir
	lateinit var home: Path

	private val scope = CoroutineScope(Dispatchers.Unconfined)
	private val transport = FakeTransport()

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
	fun `requiring item data before the repo is installed is a no-op`() {
		ItemRepo.require()

		assertEquals(RepoState.IDLE, ItemRepo.state)
		assertEquals(0, ItemRepo.required)
	}

	private fun install() {
		val root = home.resolve("repo")
		ItemRepo.install(scope, root, RepoSync(DataFixture.NEU, root, transport))
	}

	private class FakeTransport : RepoTransport {
		var reachable = true
		var downloads = 0

		override fun text(url: String): String? = if (reachable) "{\"sha\":\"abc123\"}" else null

		override fun download(url: String, destination: Path): Boolean {
			downloads++
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
