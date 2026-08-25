package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.WebResponse
import io.github.dzkchen.dhen.util.text
import org.slf4j.LoggerFactory
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.time.Duration

internal data class RepoSource(val owner: String, val repo: String, val branch: String) {
	val commitUrl: String get() = "https://api.github.com/repos/$owner/$repo/commits/$branch"

	fun archiveUrl(commit: String): String = "https://github.com/$owner/$repo/archive/$commit.zip"
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
	private val web: WebClient = WebClient(mapOf("Accept" to "application/vnd.github+json"))
) : RepoTransport {
	override fun response(url: String): WebResponse = web.response(url)

	override fun download(url: String, destination: Path): Boolean {
		destination.parent?.let(Files::createDirectories)
		return web.send(url, HttpResponse.BodyHandlers.ofFile(destination)) != null
	}
}

internal class RepoSync(
	private val source: RepoSource,
	private val root: Path,
	private val transport: RepoTransport = HttpRepoTransport()
) {
	private val marker: Path = root.resolveSibling("${root.fileName}.commit")
	private val archive: Path = root.resolveSibling("${root.fileName}.zip")

	fun syncedCommit(): String? = if (Files.isRegularFile(marker)) Files.readString(marker).trim().ifEmpty { null } else null

	fun hasContent(): Boolean = Files.isDirectory(root) && Files.list(root).use { it.findFirst().isPresent }

	fun sync(stillWanted: () -> Boolean = { true }): SyncOutcome {
		val latest = latestCommit()
		val commit = latest.commit ?: return SyncOutcome(SyncResult.UNREACHABLE, latest.retryAfter)
		if (commit == syncedCommit() && hasContent()) return SyncOutcome(SyncResult.UP_TO_DATE)
		return try {
			if (!stillWanted()) return SyncOutcome(SyncResult.ABANDONED)
			if (!transport.download(source.archiveUrl(commit), archive)) return SyncOutcome(SyncResult.UNREACHABLE)
			unpack()
			Files.writeString(marker, commit)
			SyncOutcome(SyncResult.UPDATED)
		} catch (throwable: Throwable) {
			log.error("Dhen could not unpack the {} repo", source.repo, throwable)
			SyncOutcome(SyncResult.UNREADABLE)
		} finally {
			Files.deleteIfExists(archive)
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

	private fun unpack() {
		Files.deleteIfExists(marker)
		root.toFile().deleteRecursively()
		Files.createDirectories(root)
		ZipInputStream(Files.newInputStream(archive).buffered()).use { zip ->
			var entry = zip.nextEntry
			while (entry != null) {
				val relative = entry.name.substringAfter('/', "")
				if (relative.isNotEmpty()) write(relative, entry.isDirectory, zip)
				entry = zip.nextEntry
			}
		}
	}

	private fun write(relative: String, directory: Boolean, zip: ZipInputStream) {
		val target = root.resolve(relative).normalize()
		require(target.startsWith(root)) { "Repo archive entry '$relative' escapes ${root.fileName}" }
		if (directory) {
			Files.createDirectories(target)
			return
		}
		target.parent?.let(Files::createDirectories)
		Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
	}

	private companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
