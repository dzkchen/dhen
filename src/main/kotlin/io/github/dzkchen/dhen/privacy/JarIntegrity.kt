package io.github.dzkchen.dhen.privacy

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.TamperWarningScreen
import io.github.dzkchen.dhen.util.Failsafe
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

internal object JarIntegrity {
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	private val failsafe = Failsafe("Dhen {} failed, its tamper warning is off until restart")

	@Volatile
	private var result: IntegrityResult = IntegrityResult.Pending

	private var dismissed = false
	private var shown = false
	private var persistDismissed: () -> Unit = {}
	private var openRelease: (String) -> Unit = {}

	val warningDismissed: Boolean
		get() = dismissed

	fun install(
		scope: CoroutineScope,
		jarPath: Path?,
		minecraftVersion: String?,
		modVersion: String,
		warningDismissed: Boolean,
		persistDismissed: () -> Unit,
		openRelease: (String) -> Unit
	) {
		dismissed = warningDismissed
		this.persistDismissed = persistDismissed
		this.openRelease = openRelease
		scope.launch {
			result = check(jarPath, minecraftVersion, modVersion)
		}
	}

	fun tick(client: Minecraft) {
		failsafe.guard("jar integrity check") {
			val parent = client.gui.screen()
			if (parent !is TitleScreen || shown || dismissed) return
			val mismatch = result as? IntegrityResult.Tampered ?: return
			shown = true
			client.gui.setScreen(
				TamperWarningScreen(parent, mismatch.expected, mismatch.actual, mismatch.releaseUrl, ::dismiss, openRelease)
			)
		}
	}

	private fun dismiss() {
		if (dismissed) return
		dismissed = true
		persistDismissed()
	}

	private fun check(jarPath: Path?, minecraftVersion: String?, modVersion: String): IntegrityResult {
		if (jarPath == null || !Files.isRegularFile(jarPath) || !jarPath.fileName.toString().endsWith(JAR_SUFFIX)) {
			return skipped("not running from a jar")
		}
		if (minecraftVersion.isNullOrBlank()) return skipped("Minecraft version is unavailable")
		return try {
			val actual = sha256(jarPath)
			val releaseUrl = "$RELEASES_PAGE/v$modVersion"
			val response = release(modVersion)
			if (response.statusCode() != OK) {
				skipped("release lookup answered ${response.statusCode()}")
			} else {
				val checked = JarIntegrityVerifier.verify(response.body(), minecraftVersion, modVersion, actual, releaseUrl)
				when (checked) {
					is IntegrityResult.Clean -> log.debug("Dhen jar integrity verified")
					is IntegrityResult.Tampered -> log.warn(
						"Dhen jar integrity check failed. Expected: {}, actual: {}",
						checked.expected,
						checked.actual
					)
					is IntegrityResult.Skipped -> log.debug("Dhen jar integrity check skipped: {}", checked.reason)
					IntegrityResult.Pending -> Unit
				}
				checked
			}
		} catch (throwable: Throwable) {
			log.debug("Dhen jar integrity check skipped after a failure", throwable)
			IntegrityResult.Skipped("check failed")
		}
	}

	private fun release(modVersion: String): HttpResponse<String> {
		val timeout = Duration.ofSeconds(TIMEOUT_SECONDS)
		val client = HttpClient.newBuilder()
			.connectTimeout(timeout)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build()
		val request = HttpRequest.newBuilder()
			.uri(URI.create("$RELEASES_API/v$modVersion"))
			.header("User-Agent", "Dhen/$modVersion")
			.header("Accept", GITHUB_JSON)
			.timeout(timeout)
			.GET()
			.build()
		return client.send(request, HttpResponse.BodyHandlers.ofString())
	}

	private fun sha256(path: Path): String {
		val digest = MessageDigest.getInstance(SHA_256)
		Files.newInputStream(path).use { input ->
			val buffer = ByteArray(HASH_BUFFER_SIZE)
			while (true) {
				val count = input.read(buffer)
				if (count < 0) break
				digest.update(buffer, 0, count)
			}
		}
		return HexFormat.of().formatHex(digest.digest())
	}

	private fun skipped(reason: String): IntegrityResult.Skipped {
		log.debug("Dhen jar integrity check skipped: {}", reason)
		return IntegrityResult.Skipped(reason)
	}

	private const val OK = 200
	private const val TIMEOUT_SECONDS = 10L
	private const val HASH_BUFFER_SIZE = 8192
	private const val JAR_SUFFIX = ".jar"
	private const val SHA_256 = "SHA-256"
	private const val GITHUB_JSON = "application/vnd.github.v3+json"
	private const val RELEASES_API = "https://api.github.com/repos/dzkchen/dhen/releases/tags"
	private const val RELEASES_PAGE = "https://github.com/dzkchen/dhen/releases/tag"
}

internal sealed interface IntegrityResult {
	data object Pending : IntegrityResult
	data class Skipped(val reason: String) : IntegrityResult
	data class Clean(val expected: String, val actual: String) : IntegrityResult
	data class Tampered(val expected: String, val actual: String, val releaseUrl: String) : IntegrityResult
}

internal object JarIntegrityVerifier {
	fun verify(
		releaseJson: String,
		minecraftVersion: String,
		modVersion: String,
		actualDigest: String,
		releaseUrl: String
	): IntegrityResult {
		val root = try {
			JsonParser.parseString(releaseJson) as? JsonObject
		} catch (_: RuntimeException) {
			null
		} ?: return IntegrityResult.Skipped("release response is not an object")
		val assets = root.array("assets") ?: return IntegrityResult.Skipped("release has no assets")
		var expected: String? = null
		for (element in assets) {
			val asset = element as? JsonObject ?: continue
			if (!matches(asset.text("name"), minecraftVersion, modVersion)) continue
			expected = asset.text("digest")?.takeIf { it.startsWith(DIGEST_PREFIX) }?.removePrefix(DIGEST_PREFIX)
			break
		}
		val expectedBytes = digestBytes(expected) ?: return IntegrityResult.Skipped("matching asset has no SHA-256 digest")
		val actualBytes = digestBytes(actualDigest) ?: return IntegrityResult.Skipped("local SHA-256 digest is invalid")
		val normalizedExpected = expected!!.lowercase()
		val normalizedActual = actualDigest.lowercase()
		return if (MessageDigest.isEqual(expectedBytes, actualBytes)) {
			IntegrityResult.Clean(normalizedExpected, normalizedActual)
		} else {
			IntegrityResult.Tampered(normalizedExpected, normalizedActual, releaseUrl)
		}
	}

	private fun matches(name: String?, minecraftVersion: String, modVersion: String): Boolean {
		if (name == null || !name.startsWith(ASSET_PREFIX) || !name.endsWith(JAR_SUFFIX)) return false
		val versionSeparator = name.indexOf(MOD_VERSION_MARKER, ASSET_PREFIX.length)
		if (versionSeparator < 0) return false
		val assetVersion = name.substring(versionSeparator + MOD_VERSION_MARKER.length, name.length - JAR_SUFFIX.length)
		if (assetVersion != modVersion) return false
		val range = name.substring(ASSET_PREFIX.length, versionSeparator)
		val dash = range.indexOf('-')
		return if (dash < 0) range == minecraftVersion else {
			val minimum = range.substring(0, dash)
			val maximum = range.substring(dash + 1)
			compareVersions(minecraftVersion, minimum)?.let { lower ->
				lower >= 0 && (compareVersions(minecraftVersion, maximum) ?: 1) <= 0
			} == true
		}
	}

	private fun compareVersions(left: String, right: String): Int? {
		val leftParts = numericVersion(left) ?: return null
		val rightParts = numericVersion(right) ?: return null
		for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
			val comparison = (leftParts.getOrNull(index) ?: 0).compareTo(rightParts.getOrNull(index) ?: 0)
			if (comparison != 0) return comparison
		}
		return 0
	}

	private fun numericVersion(version: String): List<Int>? {
		val parts = ArrayList<Int>()
		for (part in version.split('.')) parts += part.toIntOrNull() ?: return null
		return parts
	}

	private fun digestBytes(value: String?): ByteArray? {
		if (value == null || value.length != DIGEST_LENGTH) return null
		return try {
			HexFormat.of().parseHex(value)
		} catch (_: IllegalArgumentException) {
			null
		}
	}

	private const val ASSET_PREFIX = "dhen-"
	private const val MOD_VERSION_MARKER = "+v"
	private const val JAR_SUFFIX = ".jar"
	private const val DIGEST_PREFIX = "sha256:"
	private const val DIGEST_LENGTH = 64
}
