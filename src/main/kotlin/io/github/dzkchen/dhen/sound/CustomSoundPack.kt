package io.github.dzkchen.dhen.sound

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.floats.FloatConsumer
import io.github.dzkchen.dhen.Dhen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.minecraft.SharedConstants
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackCompatibility
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.world.flag.FeatureFlagSet
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Optional
import java.util.function.Consumer

internal object CustomSoundPack {
	const val PACK_ID = "dhen_custom_sounds"
	const val DIRECTORY = "sounds"
	internal val acceptedExtensions: Set<String> = setOf("ogg")

	private const val CUSTOM_PREFIX = "custom/"
	private const val SOUNDS_ROOT = "sounds/"
	private const val MANIFEST_PATH = "sounds.json"
	private const val METADATA_PATH = "pack.mcmeta"
	private const val TITLE = "Dhen Custom Sounds"
	private const val DESCRIPTION = "Audio files from config/dhen/sounds"
	private val legalBase = Regex("[a-z0-9_.-]+")
	private val namespaces = setOf(Dhen.MOD_ID)
	private val stateLock = Any()
	private val location = PackLocationInfo(PACK_ID, Component.literal(TITLE), PackSource.BUILT_IN, Optional.empty())
	private val metadata = Pack.Metadata(Component.literal(DESCRIPTION), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), emptyList())
	private val selection = PackSelectionConfig(true, Pack.Position.TOP, true)
	private val source = RepositorySource(::contribute)
	private val acceptedFormatsLabel = acceptedExtensions.joinToString(" / ") { it.uppercase(Locale.ROOT) }

	@Volatile
	private var folder: Path? = null
	@Volatile
	private var snapshot = Snapshot.EMPTY
	private var generation = 0L

	fun install(configRoot: Path, scope: CoroutineScope, afterRefresh: suspend (ScanResult) -> Unit = {}) {
		synchronized(stateLock) {
			folder = configRoot.resolve(DIRECTORY)
			snapshot = Snapshot.EMPTY
			generation++
		}
		scope.launch { afterRefresh(refresh()) }
	}

	fun uninstall() = synchronized(stateLock) {
		generation++
		folder = null
		snapshot = Snapshot.EMPTY
	}

	internal fun refresh(validate: (Path) -> Unit = ::validateOgg): ScanResult {
		val attempt = synchronized(stateLock) {
			val target = folder ?: return ScanResult(false, 0, null, generation)
			ScanAttempt(++generation, target)
		}
		val scanned = try {
			scan(attempt.folder, validate)
		} catch (exception: Exception) {
			log.warn("Could not read custom sounds under {}", attempt.folder, exception)
			Scanned(Snapshot.EMPTY, exception)
		}
		return synchronized(stateLock) {
			if (folder != attempt.folder || generation != attempt.generation) {
				ScanResult(false, scanned.snapshot.files.size, scanned.failure, attempt.generation)
			} else {
				snapshot = scanned.snapshot
				ScanResult(true, scanned.snapshot.files.size, scanned.failure, attempt.generation)
			}
		}
	}

	internal fun prepareFolder(): Path? {
		val target = folder ?: return null
		Files.createDirectories(target)
		return synchronized(stateLock) { target.takeIf { folder == target } }
	}

	internal fun ownsFolder(target: Path): Boolean = synchronized(stateLock) { folder == target }

	internal fun runIfCurrent(result: ScanResult, action: () -> Unit): Boolean = synchronized(stateLock) {
		if (!result.published || generation != result.generation || folder == null) return@synchronized false
		action()
		true
	}

	internal fun identifiers(): List<Identifier> = snapshot.files.map(CustomSoundFile::event)

	internal fun formatOf(identifier: Identifier): String? {
		val current = snapshot
		for (file in current.files) {
			if (file.event == identifier) return file.extension
		}
		return null
	}

	internal fun isMissingCustom(identifier: Identifier): Boolean {
		if (!isCustom(identifier)) return false
		val current = snapshot
		for (file in current.files) {
			if (file.event == identifier) return false
		}
		return true
	}

	internal fun isCustom(identifier: Identifier): Boolean =
		identifier.namespace == Dhen.MOD_ID && identifier.path.startsWith(CUSTOM_PREFIX)

	@JvmStatic
	fun repositorySource(): RepositorySource = source

	@JvmStatic
	fun isOwnPack(pack: Pack): Boolean = pack.id == PACK_ID

	private fun contribute(output: Consumer<Pack>) {
		output.accept(Pack(location, Resources, metadata, selection))
	}

	private fun scan(target: Path, validate: (Path) -> Unit): Scanned {
		Files.createDirectories(target)
		val files = ArrayList<CustomSoundFile>()
		val identifiers = HashSet<Identifier>()
		Files.list(target).use { paths ->
			paths.sorted().forEach { path ->
				if (Files.isRegularFile(path)) add(path, validate, identifiers, files)
			}
		}
		val accepted = files.toTypedArray()
		return Scanned(Snapshot(accepted, manifest(accepted)), null)
	}

	private fun add(
		file: Path,
		validate: (Path) -> Unit,
		identifiers: MutableSet<Identifier>,
		files: MutableList<CustomSoundFile>
	) {
		val name = file.fileName.toString()
		val separator = name.lastIndexOf('.')
		val extension = if (separator < 0) "" else name.substring(separator + 1).lowercase(Locale.ROOT)
		if (extension !in acceptedExtensions) {
			log.warn("Skipping custom sound '{}': supported formats are {}", name, acceptedFormats())
			return
		}
		val base = name.substring(0, separator)
		if (!legalBase.matches(base)) {
			log.warn("Skipping custom sound '{}': the base name must match {}", name, legalBase.pattern)
			return
		}
		try {
			validate(file)
		} catch (exception: Exception) {
			log.warn("Skipping custom sound '{}': it is not readable Ogg Vorbis audio", name, exception)
			return
		}
		val event = Dhen.id(CUSTOM_PREFIX + base)
		if (!identifiers.add(event)) {
			log.warn("Skipping custom sound '{}': another file already provides {}", name, event)
			return
		}
		files += CustomSoundFile(event, Dhen.id(SOUNDS_ROOT + CUSTOM_PREFIX + base + "." + extension), file, extension)
	}

	private fun validateOgg(file: Path) {
		Files.newInputStream(file).use { input ->
			JOrbisAudioStream(input).use { audio ->
				while (audio.readChunk(discardSample)) {
				}
			}
		}
	}

	private fun manifest(files: Array<CustomSoundFile>): ByteArray {
		val document = JsonObject()
		for (file in files) {
			val sound = JsonObject()
			sound.addProperty("name", file.event.toString())
			sound.addProperty("stream", true)
			document.add(file.event.path, JsonObject().apply { add("sounds", JsonArray().apply { add(sound) }) })
		}
		return document.toString().toByteArray(StandardCharsets.UTF_8)
	}

	private fun metadata(): ByteArray {
		val format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES)
		val encoded = JsonArray().apply { add(format.major); add(format.minor) }
		val pack = JsonObject()
		pack.addProperty("description", DESCRIPTION)
		pack.add("min_format", encoded.deepCopy())
		pack.add("max_format", encoded)
		return JsonObject().apply { add("pack", pack) }.toString().toByteArray(StandardCharsets.UTF_8)
	}

	internal fun acceptedFormats(): String = acceptedFormatsLabel

	internal data class ScanResult(val published: Boolean, val count: Int, val failure: Exception?, internal val generation: Long)

	private data class ScanAttempt(val generation: Long, val folder: Path)

	private data class Scanned(val snapshot: Snapshot, val failure: Exception?)

	private data class CustomSoundFile(
		val event: Identifier,
		val resource: Identifier,
		val file: Path,
		val extension: String
	)

	private class Snapshot(val files: Array<CustomSoundFile>, val manifest: ByteArray) {
		companion object {
			val EMPTY = Snapshot(emptyArray(), "{}".toByteArray(StandardCharsets.UTF_8))
		}
	}

	private object Resources : Pack.ResourcesSupplier {
		override fun openPrimary(location: PackLocationInfo): PackResources = CustomResources(location, snapshot)

		override fun openFull(location: PackLocationInfo, metadata: Pack.Metadata): PackResources = CustomResources(location, snapshot)
	}

	private class CustomResources(private val info: PackLocationInfo, private val content: Snapshot) : PackResources {
		override fun getRootResource(vararg elements: String): IoSupplier<InputStream>? =
			if (elements.size == 1 && elements[0] == METADATA_PATH) IoSupplier { ByteArrayInputStream(metadata()) } else null

		override fun getResource(type: PackType, id: Identifier): IoSupplier<InputStream>? {
			if (type != PackType.CLIENT_RESOURCES || id.namespace != Dhen.MOD_ID) return null
			if (id.path == MANIFEST_PATH) return IoSupplier { ByteArrayInputStream(content.manifest) }
			for (file in content.files) {
				if (file.resource == id) return IoSupplier.create(file.file)
			}
			return null
		}

		override fun listResources(type: PackType, namespace: String, directory: String, output: PackResources.ResourceOutput) {
			if (type != PackType.CLIENT_RESOURCES || namespace != Dhen.MOD_ID) return
			for (file in content.files) {
				val path = file.resource.path
				if (path == directory || path.startsWith("$directory/")) output.accept(file.resource, IoSupplier.create(file.file))
			}
		}

		override fun getNamespaces(type: PackType): Set<String> = if (type == PackType.CLIENT_RESOURCES) namespaces else emptySet()

		override fun <T : Any> getMetadataSection(type: MetadataSectionType<T>): T? = null

		override fun location(): PackLocationInfo = info

		override fun close() = Unit
	}

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val discardSample = FloatConsumer { }
}
