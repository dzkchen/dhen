package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.WebResponse
import io.github.dzkchen.dhen.util.text
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration as JavaDuration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.zip.ZipFile
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.Duration

internal data class RepoSource(val owner: String, val repo: String, val branch: String) {
	val commitUrl: String get() = "https://api.github.com/repos/$owner/$repo/commits/$branch"

	fun archiveUrl(commit: String): String = "https://github.com/$owner/$repo/archive/$commit.zip"

	fun rawUrl(commit: String, path: String): String =
		"https://raw.githubusercontent.com/$owner/$repo/$commit/$path"
}

internal enum class SyncResult {
	UPDATED,
	UP_TO_DATE,
	ABANDONED,
	UNREACHABLE,
	UNREADABLE
}

internal data class SyncOutcome(val result: SyncResult, val retryAfter: Duration? = null)

internal interface RepoTransport {
	fun response(url: String): WebResponse

	fun download(url: String, destination: Path): Boolean
}

internal class HttpRepoTransport(
	private val web: WebClient = WebClient(mapOf("Accept" to "application/vnd.github+json")),
	private val archiveDeadline: JavaDuration = ARCHIVE_DEADLINE
) : RepoTransport {
	override fun response(url: String): WebResponse = web.response(url)

	override fun download(url: String, destination: Path): Boolean {
		destination.parent?.let(Files::createDirectories)
		return web.send(url, HttpResponse.BodyHandlers.ofFile(destination), archiveDeadline) != null
	}

	private companion object {
		private val ARCHIVE_DEADLINE: JavaDuration = JavaDuration.ofMinutes(5)
	}
}

internal class RepoSync(
	private val source: RepoSource,
	root: Path,
	private val transport: RepoTransport = HttpRepoTransport()
) {
	private val root: Path = root.toAbsolutePath().normalize()
	private val marker: Path = this.root.resolveSibling("${this.root.fileName}.commit")
	private val legacyArchive: Path = this.root.resolveSibling("${this.root.fileName}.zip")
	private val files = fileStates.computeIfAbsent(this.root) { FileState() }

	fun syncedCommit(): String? = files.access.read { syncedCommitLocked() }

	internal fun <T> readMarked(read: (Path, String) -> T): T? = files.access.read {
		val commit = syncedCommitLocked() ?: return@read null
		if (!hasContentLocked()) return@read null
		read(root, commit)
	}

	internal fun changeInstallation(change: () -> Unit) = synchronized(files) {
		files.publication++
		change()
	}

	fun sync(stillWanted: () -> Boolean = { true }): SyncOutcome {
		val publication = synchronized(files) { ++files.publication }
		val latest = latestCommit()
		val commit = latest.commit ?: return SyncOutcome(SyncResult.UNREACHABLE, latest.retryAfter)
		if (isCurrent(commit)) return SyncOutcome(SyncResult.UP_TO_DATE)
		return try {
			refresh(commit, publication, stillWanted)
		} catch (throwable: Throwable) {
			log.error("Dhen could not stage or publish the {} repo", source.repo, throwable)
			SyncOutcome(SyncResult.UNREADABLE)
		}
	}

	private fun refresh(commit: String, publication: Long, stillWanted: () -> Boolean): SyncOutcome {
		val staging = stagingDirectory()
		val archive = staging.resolve(ARCHIVE)
		val candidateRoot = staging.resolve(CANDIDATE_ROOT)
		val candidateMarker = staging.resolve(CANDIDATE_MARKER)
		var transactionComplete = false
		return try {
			if (!stillWanted()) return SyncOutcome(SyncResult.ABANDONED)
			if (!transport.download(source.archiveUrl(commit), archive)) return SyncOutcome(SyncResult.UNREACHABLE)
			unpack(archive, candidateRoot)
			require(hasContent(candidateRoot)) { "Repo archive contains no files" }
			Files.writeString(candidateMarker, commit)
			if (!publish(staging, candidateRoot, candidateMarker, publication)) {
				return SyncOutcome(SyncResult.ABANDONED)
			}
			transactionComplete = true
			SyncOutcome(SyncResult.UPDATED)
		} finally {
			if (transactionComplete || !hasRecoveryFiles(staging)) deleteTree(staging)
			clearLegacyArchive()
		}
	}

	private fun stagingDirectory(): Path {
		val parent = root.parent ?: throw IllegalStateException("Repo path has no parent")
		Files.createDirectories(parent)
		return Files.createTempDirectory(parent, ".${root.fileName}-sync-")
	}

	private fun publish(
		staging: Path,
		candidateRoot: Path,
		candidateMarker: Path,
		publication: Long
	): Boolean = files.access.write {
		synchronized(files) {
			if (publication != files.publication) return@synchronized false
			val backupRoot = staging.resolve(BACKUP_ROOT)
			val backupMarker = staging.resolve(BACKUP_MARKER)
			var rootBackedUp = false
			var markerBackedUp = false
			var rootPublished = false
			var markerPublished = false
			try {
				if (Files.exists(marker)) {
					move(marker, backupMarker)
					markerBackedUp = true
				}
				if (Files.exists(root)) {
					move(root, backupRoot)
					rootBackedUp = true
				}
				move(candidateRoot, root)
				rootPublished = true
				move(candidateMarker, marker)
				markerPublished = true
				true
			} catch (throwable: Throwable) {
				rollback(
					candidateRoot,
					candidateMarker,
					backupRoot,
					backupMarker,
					rootBackedUp,
					markerBackedUp,
					rootPublished,
					markerPublished
				)?.let(throwable::addSuppressed)
				throw throwable
			}
		}
	}

	private fun rollback(
		candidateRoot: Path,
		candidateMarker: Path,
		backupRoot: Path,
		backupMarker: Path,
		rootBackedUp: Boolean,
		markerBackedUp: Boolean,
		rootPublished: Boolean,
		markerPublished: Boolean
	): Throwable? {
		var failure: Throwable? = null
		if (markerPublished) failure = rollbackStep(failure) { move(marker, candidateMarker) }
		if (rootPublished) failure = rollbackStep(failure) { move(root, candidateRoot) }
		if (rootBackedUp) failure = rollbackStep(failure) { move(backupRoot, root) }
		if (markerBackedUp) failure = rollbackStep(failure) { move(backupMarker, marker) }
		return failure
	}

	private fun rollbackStep(failure: Throwable?, action: () -> Unit): Throwable? = try {
		action()
		failure
	} catch (throwable: Throwable) {
		if (failure == null) throwable else failure.apply { addSuppressed(throwable) }
	}

	private fun move(source: Path, destination: Path) {
		Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
	}

	private fun isCurrent(commit: String): Boolean = files.access.read {
		commit == syncedCommitLocked() && hasContentLocked()
	}

	private fun syncedCommitLocked(): String? =
		if (Files.isRegularFile(marker)) Files.readString(marker).trim().ifEmpty { null } else null

	private fun hasContentLocked(): Boolean = hasContent(root)

	private fun hasContent(directory: Path): Boolean =
		Files.isDirectory(directory) && Files.list(directory).use { contents ->
			contents.anyMatch { Files.isRegularFile(it) || hasContent(it) }
		}

	private fun hasRecoveryFiles(staging: Path): Boolean =
		Files.exists(staging.resolve(BACKUP_ROOT)) || Files.exists(staging.resolve(BACKUP_MARKER))

	private fun deleteTree(path: Path) {
		if (!path.toFile().deleteRecursively() && Files.exists(path)) {
			log.warn("Dhen could not remove repo staging path {}", path)
		}
	}

	private fun clearLegacyArchive() {
		try {
			Files.deleteIfExists(legacyArchive)
		} catch (throwable: Throwable) {
			log.warn("Dhen could not remove legacy repo archive {}", legacyArchive, throwable)
		}
	}

	private fun latestCommit(): LatestCommit {
		val response = transport.response(source.commitUrl)
		val body = response.body ?: return LatestCommit(null, response.retryAfter)
		val commit = try {
			JsonParser.parseString(body).asJsonObject.text("sha")
		} catch (throwable: Throwable) {
			log.warn("Dhen could not read the latest {} commit", source.repo, throwable)
			null
		}
		return LatestCommit(commit, response.retryAfter)
	}

	private class LatestCommit(val commit: String?, val retryAfter: Duration?)

	private fun unpack(archive: Path, destination: Path) {
		Files.createDirectories(destination)
		ZipFile(archive.toFile()).use { zip ->
			val entries = zip.entries()
			while (entries.hasMoreElements()) {
				val entry = entries.nextElement()
				val relative = entry.name.substringAfter('/', "")
				if (relative.isEmpty()) continue
				if (entry.isDirectory) {
					write(destination, relative, true, null)
				} else {
					zip.getInputStream(entry).use { input -> write(destination, relative, false, input) }
				}
			}
		}
	}

	private fun write(destination: Path, relative: String, directory: Boolean, input: InputStream?) {
		val target = destination.resolve(relative).normalize()
		require(target.startsWith(destination)) { "Repo archive entry '$relative' escapes ${root.fileName}" }
		if (directory) {
			Files.createDirectories(target)
			return
		}
		target.parent?.let(Files::createDirectories)
		Files.copy(requireNotNull(input), target, StandardCopyOption.REPLACE_EXISTING)
	}

	private companion object {
		private const val ARCHIVE = "repo.zip"
		private const val CANDIDATE_ROOT = "candidate-repo"
		private const val CANDIDATE_MARKER = "candidate.commit"
		private const val BACKUP_ROOT = "previous-repo"
		private const val BACKUP_MARKER = "previous.commit"
		private val fileStates = ConcurrentHashMap<Path, FileState>()
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}

	private class FileState(
		var publication: Long = 0L,
		val access: ReentrantReadWriteLock = ReentrantReadWriteLock()
	)
}
