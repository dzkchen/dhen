package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

internal data class RepoSource(val owner: String, val repo: String, val branch: String) {
	val commitUrl: String get() = "https://api.github.com/repos/$owner/$repo/commits/$branch"

	fun archiveUrl(commit: String): String = "https://github.com/$owner/$repo/archive/$commit.zip"
}

internal enum class SyncResult {
	UPDATED,
	UP_TO_DATE,
	UNREACHABLE,
	UNREADABLE
}

internal interface RepoTransport {
	fun text(url: String): String?

	fun download(url: String, destination: Path): Boolean
}

internal class HttpRepoTransport(
	private val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
) : RepoTransport {
	override fun text(url: String): String? = send(url, HttpResponse.BodyHandlers.ofString())

	override fun download(url: String, destination: Path): Boolean {
		destination.parent?.let(Files::createDirectories)
		return send(url, HttpResponse.BodyHandlers.ofFile(destination)) != null
	}

	private fun <T> send(url: String, body: HttpResponse.BodyHandler<T>): T? = try {
		val request = HttpRequest.newBuilder(URI.create(url))
			.header("User-Agent", Dhen.MOD_ID)
			.header("Accept", "application/vnd.github+json")
			.build()
		val response = client.send(request, body)
		if (response.statusCode() == OK) response.body() else {
			log.warn("Dhen repo request to {} answered {}", url, response.statusCode())
			null
		}
	} catch (throwable: Throwable) {
		log.warn("Dhen repo request to {} failed", url, throwable)
		null
	}

	private companion object {
		private const val OK = 200
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
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

	fun sync(): SyncResult {
		val latest = latestCommit() ?: return SyncResult.UNREACHABLE
		if (latest == syncedCommit() && hasContent()) return SyncResult.UP_TO_DATE
		return try {
			if (!transport.download(source.archiveUrl(latest), archive)) return SyncResult.UNREACHABLE
			unpack()
			Files.writeString(marker, latest)
			SyncResult.UPDATED
		} catch (throwable: Throwable) {
			log.error("Dhen could not unpack the {} repo", source.repo, throwable)
			SyncResult.UNREADABLE
		} finally {
			Files.deleteIfExists(archive)
		}
	}

	private fun latestCommit(): String? {
		val body = transport.text(source.commitUrl) ?: return null
		return try {
			JsonParser.parseString(body).asJsonObject.get("sha")?.asString?.takeIf { it.isNotEmpty() }
		} catch (throwable: Throwable) {
			log.warn("Dhen could not read the latest {} commit", source.repo, throwable)
			null
		}
	}

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
