package io.github.dzkchen.dhen.privacy

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModDependency

internal class ModGraph(
	private val ids: List<String>,
	private val canonicalIds: Map<String, String>,
	private val provided: Map<String, Set<String>>,
	private val required: Map<String, Set<String>>,
	private val contained: Map<String, Set<String>>,
	private val jarHost: Map<String, String>
) {
	fun ownerOf(namespace: String, path: String): String? = when {
		namespace != VANILLA_NAMESPACE -> canonicalIds[namespace] ?: namespace
		vanillaChannel(namespace, path) -> null
		else -> attributedTo(path)
	}

	fun closureOf(seeds: Set<String>, requiredBy: MutableMap<String, String>? = null): Set<String> {
		val reached = HashSet<String>()
		for (seed in seeds) {
			if (seed in PLATFORM_IDS) continue
			walk(seed, seed, reached, requiredBy)
			reached += seed
			var below = canonicalIds[seed]
			while (below != null) {
				val above = jarHost[below] ?: break
				walk(above, seed, reached, requiredBy)
				below = above
			}
		}
		for (seed in seeds) requiredBy?.remove(seed)
		return reached
	}

	private fun attributedTo(path: String): String? {
		var best: String? = null
		for (id in ids) {
			if (id.length < SHORTEST_ATTRIBUTABLE_ID || !path.startsWith(id)) continue
			if (path.length != id.length && PATH_BOUNDARIES.indexOf(path[id.length]) < 0) continue
			if (best == null || id.length > best.length) best = id
		}
		return best
	}

	private fun walk(id: String, seed: String, reached: MutableSet<String>, requiredBy: MutableMap<String, String>?) {
		if (id in PLATFORM_IDS) return
		val canonical = canonicalIds[id] ?: return
		if (canonical in PLATFORM_IDS || !reached.add(canonical)) return
		attribute(canonical, seed, requiredBy)
		provided[canonical]?.forEach {
			reached += it
			attribute(it, seed, requiredBy)
		}
		contained[canonical]?.forEach { walk(it, seed, reached, requiredBy) }
		required[canonical]?.forEach { walk(it, seed, reached, requiredBy) }
	}

	private fun attribute(id: String, seed: String, requiredBy: MutableMap<String, String>?) {
		if (id != seed) requiredBy?.putIfAbsent(id, seed)
	}

	companion object {
		private const val SHORTEST_ATTRIBUTABLE_ID = 3
		private const val PATH_BOUNDARIES = "_/.-"
		private const val VANILLA_NAMESPACE = "minecraft"
		private const val LOADER_COMMON_NAMESPACE = "c"
		private val VANILLA_CHANNEL_PATHS = setOf("register", "unregister", "brand", "mco")
		private val PLATFORM_IDS = setOf("minecraft", "java", "fabricloader")

		fun vanillaChannel(namespace: String, path: String): Boolean =
			namespace == VANILLA_NAMESPACE && path in VANILLA_CHANNEL_PATHS

		fun loaderCommonChannel(namespace: String): Boolean = namespace == LOADER_COMMON_NAMESPACE

		fun platform(id: String): Boolean = id in PLATFORM_IDS

		fun installed(): ModGraph {
			val ids = ArrayList<String>()
			val canonicalIds = HashMap<String, String>()
			val provided = HashMap<String, Set<String>>()
			val required = HashMap<String, Set<String>>()
			val contained = HashMap<String, Set<String>>()
			val jarHost = HashMap<String, String>()
			for (container in FabricLoader.getInstance().allMods) {
				val metadata = container.metadata
				val id = metadata.id
				ids += id
				metadata.provides.takeIf { it.isNotEmpty() }?.let { provided[id] = it.toSet() }
				metadata.dependencies
					.filter { it.kind == ModDependency.Kind.DEPENDS }
					.mapTo(HashSet()) { it.modId }
					.takeIf { it.isNotEmpty() }
					?.let { required[id] = it }
				container.containedMods
					.mapTo(HashSet()) { it.metadata.id }
					.takeIf { it.isNotEmpty() }
					?.let { contained[id] = it }
				container.containingMod.ifPresent { jarHost[id] = it.metadata.id }
			}
			canonicalIds += jarInJarNamespaces(ids, contained)
			for ((id, aliases) in provided) for (alias in aliases) canonicalIds[alias] = id
			for (id in ids) canonicalIds[id] = id
			return ModGraph(ids, canonicalIds, provided, required, contained, jarHost)
		}

		fun jarInJarNamespaces(ids: List<String>, contained: Map<String, Set<String>>): Map<String, String> {
			val namespaces = HashMap<String, String>()
			for (id in ids) {
				val children = contained[id] ?: continue
				val counts = HashMap<String, Int>()
				for (child in children) {
					val dash = child.indexOf('-')
					if (dash <= 0) continue
					val prefix = child.substring(0, dash)
					if (!id.startsWith(prefix)) continue
					counts[prefix] = (counts[prefix] ?: 0) + 1
				}
				for ((prefix, count) in counts) if (count >= 2) namespaces.putIfAbsent(prefix, id)
			}
			return namespaces
		}
	}
}
