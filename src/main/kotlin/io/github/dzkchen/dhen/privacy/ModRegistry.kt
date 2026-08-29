package io.github.dzkchen.dhen.privacy

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.minecraft.resources.Identifier

object ModRegistry {
	enum class Mode { BLOCK_ALL, AUTO, CUSTOM }

	@Volatile
	var mode: Mode = Mode.AUTO

	private val graph by lazy { ModGraph.installed() }
	private val autoAllowed by lazy { graph.closureOf(registeredChannelOwners()) }

	@Volatile
	private var customAllowed: Set<String> = emptySet()

	@Volatile
	private var attribution: Map<String, String> = emptyMap()

	fun prime() {
		autoAllowed
	}

	fun whitelistable(): List<ModContainer> = FabricLoader.getInstance().allMods
		.filter { !ModGraph.platform(it.metadata.id) && it.containingMod.isEmpty }
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

	fun ownerOf(channel: Identifier): String? = graph.ownerOf(channel.namespace, channel.path)

	private fun registeredChannelOwners(): Set<String> {
		val owners = HashSet<String>()
		for (channel in ClientPlayNetworking.getGlobalReceivers()) ownerOf(channel)?.let(owners::add)
		for (channel in ClientConfigurationNetworking.getGlobalReceivers()) ownerOf(channel)?.let(owners::add)
		return owners
	}
}
