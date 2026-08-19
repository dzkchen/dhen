package io.github.dzkchen.dhen.data.repo

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

	private val source = RepoSource("NotEnoughUpdates", "NotEnoughUpdates-REPO", "master")

	@Test
	fun `a first sync downloads the archive and unpacks it without its top folder`() {
		val transport = FakeTransport("abc123", archive("items/ASPECT_OF_THE_END.json" to "{}"))

		assertEquals(SyncResult.UPDATED, sync(transport).sync())

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

		assertEquals(SyncResult.UP_TO_DATE, sync(transport).sync())
		assertEquals(0, transport.downloads)
	}

	@Test
	fun `a moved commit downloads again and replaces the old files`() {
		sync(FakeTransport("abc123", archive("items/OLD.json" to "{}"))).sync()

		val moved = FakeTransport("def456", archive("items/NEW.json" to "{}"))
		assertEquals(SyncResult.UPDATED, sync(moved).sync())

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

		assertEquals(SyncResult.UPDATED, sync(transport).sync())
		assertEquals(1, transport.downloads)
	}

	@Test
	fun `an unreachable commit endpoint leaves the last synced files alone`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()

		assertEquals(SyncResult.UNREACHABLE, sync(FakeTransport(null, ByteArray(0))).sync())
		assertTrue(Files.isRegularFile(root().resolve("items/A.json")))
		assertEquals("abc123", sync(transport).syncedCommit())
	}

	@Test
	fun `a failed download leaves the last synced files alone`() {
		val transport = FakeTransport("abc123", archive("items/A.json" to "{}"))
		sync(transport).sync()

		val broken = FakeTransport("def456", archive("items/B.json" to "{}"), downloadable = false)
		assertEquals(SyncResult.UNREACHABLE, sync(broken).sync())
		assertTrue(Files.isRegularFile(root().resolve("items/A.json")))
		assertFalse(Files.exists(home.resolve("repo.zip")))
	}

	@Test
	fun `an archive entry that climbs out of the repo directory is refused`() {
		val escaping = archive("../escaped.json" to "{}")

		assertEquals(SyncResult.UNREADABLE, sync(FakeTransport("abc123", escaping)).sync())
		assertFalse(Files.exists(home.resolve("escaped.json")))
	}

	@Test
	fun `a commit response without a sha is unreachable`() {
		val transport = FakeTransport(null, ByteArray(0), body = "{\"message\":\"Not Found\"}")

		assertEquals(SyncResult.UNREACHABLE, sync(transport).sync())
	}

	@Test
	fun `the archive is deleted once it has been unpacked`() {
		sync(FakeTransport("abc123", archive("items/A.json" to "{}"))).sync()

		assertFalse(Files.exists(home.resolve("repo.zip")))
	}

	private fun root(): Path = home.resolve("repo")

	private fun sync(transport: RepoTransport) = RepoSync(source, root(), transport)

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

		override fun text(url: String): String? = body

		override fun download(url: String, destination: Path): Boolean {
			downloads++
			if (!downloadable) return false
			destination.parent?.let(Files::createDirectories)
			Files.write(destination, zip)
			return true
		}
	}
}
