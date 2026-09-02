package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.Dhen
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import org.slf4j.LoggerFactory

internal object ServerPackCache {
	const val PACK_ID = "dhen_server_pack"

	private const val TITLE = "Server Resource Pack"
	private const val MAX_BYTES = 262144000L
	private const val TIMEOUT_MILLIS = 15000
	private const val BUFFER_BYTES = 8192
	private const val SHA1 = "SHA-1"
	private const val SHA1_LENGTH = 40
	private const val HTTP = "http"
	private const val HTTPS = "https"

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val selection = PackSelectionConfig(true, Pack.Position.BOTTOM, true)
	private val location =
		PackLocationInfo(PACK_ID, Component.literal(TITLE), PackSource.BUILT_IN, Optional.empty())

	private val cacheDir: Path by lazy {
		FabricLoader.getInstance().configDir.resolve(Dhen.MOD_ID).resolve("serverpack")
	}
	private val slots: List<Path> by lazy {
		listOf(cacheDir.resolve("pack-a.zip"), cacheDir.resolve("pack-b.zip"))
	}
	private val record: Path by lazy { cacheDir.resolve("pack.url") }

	@Volatile
	private var mounted: Pack? = null

	@Volatile
	private var wrappedId: UUID? = null

	@Volatile
	private var preparing: Job? = null

	@Volatile
	var launcher: ((suspend CoroutineScope.() -> Unit) -> Job?)? = null

	fun fetchable(url: String): Boolean {
		val target = runCatching { URI(url) }.getOrNull() ?: return false
		val scheme = target.scheme ?: return false
		if (target.host.isNullOrBlank()) return false
		return scheme.equals(HTTP, ignoreCase = true) || scheme.equals(HTTPS, ignoreCase = true)
	}

	fun requested(id: UUID, url: String, hash: String) {
		if (preparing?.isActive == true) return
		wrappedId = id
		val start = launcher ?: return
		preparing = start { mount(id, "$hash\n$url", url, hash) }
	}

	fun released(id: UUID?) {
		if (id != null && id != wrappedId) return
		wrappedId = null
		if (mounted == null) return
		mounted = null
		LanguageKeys.markMountedServerPack(null)
		reload()
	}

	@JvmStatic
	fun contribute(packs: Consumer<Pack>) {
		mounted?.let(packs::accept)
	}

	private suspend fun mount(id: UUID, key: String, url: String, hash: String) {
		val pack = withContext(Dispatchers.IO) {
			runCatching { prepare(key, url, hash) }
				.onFailure { log.warn("Could not prepare the cached server resource pack", it) }
				.getOrNull()
		} ?: return
		if (wrappedId != id) return
		mounted = pack
		LanguageKeys.markMountedServerPack(PACK_ID)
		reload()
	}

	private fun prepare(key: String, url: String, hash: String): Pack? {
		Files.createDirectories(cacheDir)
		val newest = newest()
		val cached = newest?.takeIf { recordedKey() == key }
		if (cached != null && announced(hash)) return open(cached)
		val target = if (newest == slots.first()) slots.last() else slots.first()
		return open(download(key, url, hash, target) ?: cached ?: return null)
	}

	private fun download(key: String, url: String, hash: String, target: Path): Path? {
		val temp = Files.createTempFile(cacheDir, "pack", ".tmp")
		if (!fetch(url, hash, temp)) {
			Files.deleteIfExists(temp)
			return null
		}
		return try {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
			Files.writeString(record, key)
			target
		} catch (exception: FileSystemException) {
			if (!Files.isRegularFile(target)) {
				Files.deleteIfExists(temp)
				throw exception
			}
			log.warn("Cannot replace {}, running this session off the fresh download", target, exception)
			temp.toFile().deleteOnExit()
			temp
		} catch (exception: IOException) {
			Files.deleteIfExists(temp)
			throw exception
		}
	}

	private fun fetch(url: String, hash: String, temp: Path): Boolean {
		var connection: HttpURLConnection? = null
		return try {
			val proxy = Minecraft.getInstance().proxy
			val opened = openWithTimeouts(URL(url), proxy)
			connection = opened
			val last = if (LocalUrls.guarding()) {
				LocalUrls.follow(opened, proxy, emptyMap(), ::openWithTimeouts)
			} else {
				opened
			}
			connection = last
			if (last.responseCode != HttpURLConnection.HTTP_OK) {
				log.warn("Server resource pack download answered {}, discarding", last.responseCode)
				return false
			}
			last.inputStream.use { copy(it, hash, temp) }
		} catch (exception: Exception) {
			log.warn("Could not download the server resource pack", exception)
			false
		} finally {
			connection?.disconnect()
		}
	}

	private fun openWithTimeouts(url: URL, proxy: Proxy): HttpURLConnection =
		(url.openConnection(proxy) as HttpURLConnection).apply {
			connectTimeout = TIMEOUT_MILLIS
			readTimeout = TIMEOUT_MILLIS
		}

	private fun copy(source: InputStream, hash: String, temp: Path): Boolean {
		val digest = MessageDigest.getInstance(SHA1)
		val buffer = ByteArray(BUFFER_BYTES)
		var written = 0L
		Files.newOutputStream(temp).use { sink ->
			var read = source.read(buffer)
			while (read >= 0) {
				written += read
				if (written > MAX_BYTES) {
					log.warn("Server resource pack grew past {} bytes, discarding", MAX_BYTES)
					return false
				}
				digest.update(buffer, 0, read)
				sink.write(buffer, 0, read)
				read = source.read(buffer)
			}
		}
		return matches(hash, digest)
	}

	private fun announced(hash: String): Boolean =
		hash.length == SHA1_LENGTH && hash.all { it.digitToIntOrNull(16) != null }

	private fun matches(hash: String, digest: MessageDigest): Boolean {
		if (!announced(hash)) return true
		val actual = HexFormat.of().formatHex(digest.digest())
		if (actual.equals(hash, ignoreCase = true)) return true
		log.warn("Server resource pack hashed to {} but was announced as {}, discarding", actual, hash)
		return false
	}

	private fun open(file: Path): Pack? {
		val supplier = FilePackResources.FileResourcesSupplier(file)
		val resources = object : Pack.ResourcesSupplier {
			override fun openPrimary(info: PackLocationInfo): PackResources =
				strip(supplier.openPrimary(info))

			override fun openFull(info: PackLocationInfo, metadata: Pack.Metadata): PackResources =
				strip(supplier.openFull(info, metadata))
		}
		return Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES, selection)
	}

	private fun strip(resources: PackResources): PackResources =
		wrappedId?.let { LangOnlyPack.over(resources, it) } ?: resources

	private fun newest(): Path? = slots.filter(Files::exists).maxByOrNull(Files::getLastModifiedTime)

	private fun recordedKey(): String? =
		if (Files.isRegularFile(record)) Files.readString(record) else null

	private fun reload() {
		try {
			Minecraft.getInstance().reloadResourcePacks()
		} catch (exception: Exception) {
			log.warn("Could not reload resources for the cached server resource pack", exception)
		}
	}
}
