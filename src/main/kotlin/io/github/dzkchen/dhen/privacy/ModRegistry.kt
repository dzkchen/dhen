package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.Dhen
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

object ModRegistry {
	enum class Mode { BLOCK_ALL, AUTO, CUSTOM }
	private class ShaderIndex(val owners: Map<String, String>, val complete: Boolean)

	@Volatile
	var mode: Mode = Mode.AUTO

	private val graph by lazy { ModGraph.installed() }
	private val autoAllowed by lazy { graph.closureOf(registeredChannelOwners()) }
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	@Volatile
	private var customAllowed: Set<String> = emptySet()

	@Volatile
	private var attribution: Map<String, String> = emptyMap()
	@Volatile
	private var shaderIndex = ShaderIndex(emptyMap(), false)

	fun prime() {
		autoAllowed
	}

	fun primeShaderOwners() {
		val found = HashMap<String, String>()
		var complete = true
		for (mod in installedMods()) {
			for (root in mod.rootPaths) {
				if (!scanShaderRoot(mod.metadata.id, root, found)) complete = false
			}
		}
		shaderIndex = ShaderIndex(found.toMap(), complete)
		log.info("Shader owners indexed: {} namespaces", found.size)
	}

	fun whitelistable(): List<ModContainer> {
		val roots = graph.roots()
		return FabricLoader.getInstance().allMods
			.filter { it.metadata.id in roots }
			.sortedBy { it.metadata.name.lowercase() }
	}

	fun installedMods(): List<ModContainer> = FabricLoader.getInstance().allMods
		.filter { !ModGraph.platform(it.metadata.id) }
		.sortedBy { it.metadata.name.lowercase() }

	fun select(explicit: Set<String>) {
		val requiredBy = HashMap<String, String>()
		customAllowed = graph.closureOf(explicit, requiredBy)
		attribution = requiredBy
	}

	fun requiredBy(modId: String): String? = attribution[modId]

	fun allows(channel: Identifier): Boolean {
		val namespace = channel.namespace
		val path = channel.path
		if (ModGraph.vanillaChannel(namespace, path)) return true
		if (ModGraph.loaderCommonChannel(namespace)) return false
		if (mode == Mode.BLOCK_ALL) return false
		return allowsMod(graph.ownerOf(namespace, path) ?: return false)
	}

	fun allowsMod(modId: String): Boolean = when (mode) {
		Mode.BLOCK_ALL -> false
		Mode.AUTO -> modId in autoAllowed
		Mode.CUSTOM -> modId in customAllowed
	}

	fun allowsShaderOverride(namespace: String): Boolean {
		val index = shaderIndex
		val owner = index.owners[namespace]
		if (owner != null) return allowsMod(owner)
		return index.complete
	}

	internal fun scanShaderRoot(modId: String, root: Path, owners: MutableMap<String, String>): Boolean {
		val assets = root.resolve(ASSETS)
		if (!Files.isDirectory(assets)) return true
		return try {
			Files.list(assets).use { namespaces ->
				namespaces.filter(Files::isDirectory).forEach { namespace ->
					val id = namespace.fileName.toString()
					if (id == VANILLA_NAMESPACE || id in owners) return@forEach
					val shaders = namespace.resolve(SHADERS)
					if (!Files.isDirectory(shaders)) return@forEach
					Files.walk(shaders).use { files ->
						if (files.anyMatch(Files::isRegularFile)) owners[id] = modId
					}
				}
			}
			true
		} catch (exception: Exception) {
			log.debug("Shader owner scan failed for {} at {}", modId, root, exception)
			false
		}
	}

	fun ownerOf(channel: Identifier): String? = graph.ownerOf(channel.namespace, channel.path)

	private fun registeredChannelOwners(): Set<String> {
		val owners = HashSet<String>()
		for (channel in ClientPlayNetworking.getGlobalReceivers()) ownerOf(channel)?.let(owners::add)
		for (channel in ClientConfigurationNetworking.getGlobalReceivers()) ownerOf(channel)?.let(owners::add)
		return owners
	}

	private const val ASSETS = "assets"
	private const val SHADERS = "shaders"
	private const val VANILLA_NAMESPACE = "minecraft"
}
