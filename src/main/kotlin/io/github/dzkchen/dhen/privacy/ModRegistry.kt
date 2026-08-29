package io.github.dzkchen.dhen.privacy

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.resources.Identifier

object ModRegistry {
	enum class Mode { BLOCK_ALL, AUTO }

	@Volatile
	var mode: Mode = Mode.AUTO

	private val graph by lazy { ModGraph.installed() }
	private val allowedMods by lazy { graph.closureOf(registeredChannelOwners()) }

	fun prime() {
		allowedMods
	}

	fun allows(channel: Identifier): Boolean {
		val namespace = channel.namespace
		val path = channel.path
		if (ModGraph.vanillaChannel(namespace, path)) return true
		if (ModGraph.loaderCommonChannel(namespace)) return false
		if (mode == Mode.BLOCK_ALL) return false
		return allowsMod(graph.ownerOf(namespace, path) ?: return false)
	}

	fun allowsMod(modId: String): Boolean = mode != Mode.BLOCK_ALL && modId in allowedMods

	fun ownerOf(channel: Identifier): String? = graph.ownerOf(channel.namespace, channel.path)

	private fun registeredChannelOwners(): Set<String> {
		val owners = HashSet<String>()
		for (channel in ClientPlayNetworking.getGlobalReceivers()) ownerOf(channel)?.let(owners::add)
		for (channel in ClientConfigurationNetworking.getGlobalReceivers()) ownerOf(channel)?.let(owners::add)
		return owners
	}
}
