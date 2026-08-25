package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.DataFixture
import io.github.dzkchen.dhen.util.WebResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RepoSyncTest {
	@TempDir
	lateinit var home: Path

	@Test
	fun `a first sync downloads the archive and unpacks it without its top folder`() {
		val transport = FakeTransport("abc123", archive("items/ASPECT_OF_THE_END.json" to "{}"))

		assertEquals(SyncResult.UPDATED, sync(transport).sync().result)

		assertTrue(Files.isRegularFile(root().resolve("items/ASPECT_OF_THE_END.json")))
		assertFalse(Files.exists(root().resolve("NotEnoughUpdates-REPO-abc123")))
	}

	@Test
	fun `the synced commit is written only after the archive unpacked`() {
		val repo = sync(FakeTransport("abc123", archive("items/A.json" to "{}")))
		repo.sync()

		assertEquals("abc123", repo.syncedCommit())
	}

	@Test
	fun `an unchanged commit downloads nothing`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()
		transport.downloads = 0

		assertEquals(SyncResult.UP_TO_DATE, sync(transport).sync().result)
		assertEquals(0, transport.downloads)
	}

	@Test
	fun `a moved commit downloads again and replaces the old files`() {
		sync(FakeTransport("abc123", archive("items/OLD.json" to "{}"))).sync()

		val moved = FakeTransport("def456", archive("items/NEW.json" to "{}"))
		assertEquals(SyncResult.UPDATED, sync(moved).sync().result)

		assertTrue(Files.isRegularFile(root().resolve("items/NEW.json")))
		assertFalse(Files.exists(root().resolve("items/OLD.json")))
		assertEquals("def456", sync(moved).syncedCommit())
	}

	@Test
	fun `an emptied repo directory re-downloads even on the same commit`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()
		root().toFile().deleteRecursively()
		transport.downloads = 0

		assertEquals(SyncResult.UPDATED, sync(transport).sync().result)
		assertEquals(1, transport.downloads)
	}

	@Test
	fun `an unreachable commit endpoint leaves the last synced files alone`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()

		assertEquals(SyncResult.UNREACHABLE, sync(FakeTransport(null, ByteArray(0))).sync().result)
		assertTrue(Files.isRegularFile(root().resolve("items/A.json")))
		assertEquals("abc123", sync(transport).syncedCommit())
	}

	@Test
	fun `a failed download leaves the last synced files alone`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()

		val broken = FakeTransport("def456", archive("items/B.json" to "{}"), downloadable = false)
		assertEquals(SyncResult.UNREACHABLE, sync(broken).sync().result)
		assertTrue(Files.isRegularFile(root().resolve("items/A.json")))
		assertFalse(Files.exists(home.resolve("repo.zip")))
	}

	@Test
	fun `an archive entry that climbs out of the repo directory is refused`() {
		val escaping = archive("../escaped.json" to "{}")

		assertEquals(SyncResult.UNREADABLE, sync(FakeTransport("abc123", escaping)).sync().result)
		assertFalse(Files.exists(home.resolve("escaped.json")))
	}

	@Test
	fun `a commit response without a sha is unreachable`() {
		val transport = FakeTransport(null, ByteArray(0), body = "{\"message\":\"Not Found\"}")

		assertEquals(SyncResult.UNREACHABLE, sync(transport).sync().result)
	}

	@Test
	fun `a sync nobody wants any more downloads nothing`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))

		assertEquals(SyncResult.ABANDONED, sync(transport).sync { false }.result)

		assertEquals(0, transport.downloads)
		assertFalse(Files.exists(root().resolve("items/A.json")))
	}

	@Test
	fun `a sync nobody wants any more leaves the last one alone and clears the archive behind it`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()
		Files.write(home.resolve("repo.zip"), ByteArray(16))

		val moved = FakeTransport("def456", archive("items/B.json" to "{}"))
		assertEquals(SyncResult.ABANDONED, sync(moved).sync { false }.result)

		assertEquals(0, moved.downloads)
		assertTrue(Files.isRegularFile(root().resolve("items/A.json")))
		assertEquals("abc123", sync(moved).syncedCommit())
		assertFalse(Files.exists(home.resolve("repo.zip")))
	}

	@Test
	fun `a repo already up to date is up to date rather than abandoned`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()

		assertEquals(SyncResult.UP_TO_DATE, sync(transport).sync { false }.result)
	}

	@Test
	fun `a sync that got as far as downloading unpacks all of it however late nobody wants it`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}", "items/B.json" to "{}"))
		var checks = 0

		assertEquals(SyncResult.UPDATED, sync(transport).sync { checks++ == 0 }.result)

		assertTrue(Files.isRegularFile(root().resolve("items/A.json")))
		assertTrue(Files.isRegularFile(root().resolve("items/B.json")))
		assertEquals("abc123", sync(transport).syncedCommit())
	}

	@Test
	fun `the archive is deleted once it has been unpacked`() {
		sync(FakeTransport("abc123", archive("items/A.json" to "{}"))).sync()

		assertFalse(Files.exists(home.resolve("repo.zip")))
	}

	private fun root(): Path = home.resolve("repo")

	private fun sync(transport: RepoTransport) = RepoSync(DataFixture.NEU, root(), transport)

	private fun archive(vararg entries: Pair<String, String>): ByteArray {
		val bytes = ByteArrayOutputStream()
		ZipOutputStream(bytes).use { zip ->
			zip.putNextEntry(ZipEntry("NotEnoughUpdates-REPO-abc123/"))
			zip.closeEntry()
			for ((name, content) in entries) {
				zip.putNextEntry(ZipEntry("NotEnoughUpdates-REPO-abc123/$name"))
				zip.write(content.toByteArray())
				zip.closeEntry()
			}
		}
		return bytes.toByteArray()
	}

	private class FakeTransport(
		private val commit: String?,
		private val zip: ByteArray,
		private val downloadable: Boolean = true,
		private val body: String? = commit?.let { "{\"sha\":\"$it\",\"commit\":{}}" }
	) : RepoTransport {
		var downloads = 0

		override fun response(url: String): WebResponse = WebResponse(body)

		override fun download(url: String, destination: Path): Boolean {
			downloads++
			if (!downloadable) return false
			destination.parent?.let(Files::createDirectories)
			Files.write(destination, zip)
			return true
		}
	}
}
