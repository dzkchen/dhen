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
			var hops = ids.size
			while (below != null && hops-- > 0) {
				val above = jarHost[below] ?: break
				walk(above, seed, reached, requiredBy)
				below = above
			}
		}
		for (seed in seeds) requiredBy?.remove(seed)
		return reached
	}

	fun roots(): Set<String> {
		val installed = ids.toHashSet()
		val modules = HashSet<String>()
		for (id in ids) {
			if (id in PLATFORM_IDS) continue
			contained[id]?.forEach { if (it != id && it in installed) modules += it }
			provided[id]?.forEach { if (it != id && it in installed) modules += it }
		}
		for ((child, host) in jarHost) {
			if (child != host && host !in PLATFORM_IDS && child in installed && host in installed) modules += child
		}
		val roots = LinkedHashSet<String>()
		for (id in ids) if (id !in PLATFORM_IDS && id !in modules) roots += id
		val reached = HashSet(closureOf(roots))
		for (id in ids) {
			if (id in PLATFORM_IDS || id in reached) continue
			roots += id
			reached += closureOf(setOf(id))
		}
		return roots
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
		private const val UMBRELLA_MODULE_FLOOR = 2

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
			val customKeys = HashMap<String, Set<String>>()
			for (container in FabricLoader.getInstance().allMods) {
				val metadata = container.metadata
				val id = metadata.id
				ids += id
				metadata.customValues.keys.takeIf { it.isNotEmpty() }?.let { customKeys[id] = it }
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
			val adopted = HashMap<String, MutableSet<String>>()
			for ((child, host) in flattenedModules(ids, customKeys, jarHost)) {
				jarHost[child] = host
				adopted.getOrPut(host) { HashSet() } += child
			}
			for ((host, children) in adopted) contained[host] = contained[host].orEmpty() + children
			canonicalIds += jarInJarNamespaces(ids, contained)
			for ((id, aliases) in provided) for (alias in aliases) canonicalIds[alias] = id
			for (id in ids) canonicalIds[id] = id
			return ModGraph(ids, canonicalIds, provided, required, contained, jarHost)
		}

		fun flattenedModules(
			ids: List<String>,
			customKeys: Map<String, Set<String>>,
			jarHost: Map<String, String>
		): Map<String, String> {
			val installed = ids.toHashSet()
			val claims = LinkedHashMap<String, String>()
			val siblings = HashMap<String, Int>()
			for (id in ids) {
				if (id in jarHost) continue
				val family = id.substringBefore('-')
				if (family == id) continue
				for (key in customKeys[id].orEmpty()) {
					val host = key.substringBefore(':')
					if (host == key || host == id || host !in installed) continue
					if (host.substringBefore('-') != family) continue
					claims[id] = host
					siblings[host] = (siblings[host] ?: 0) + 1
					break
				}
			}
			val hosts = HashMap(jarHost)
			val modules = LinkedHashMap<String, String>()
			for ((child, host) in claims) {
				if ((siblings[host] ?: 0) < UMBRELLA_MODULE_FLOOR || hostedUnder(hosts, host, child)) continue
				modules[child] = host
				hosts[child] = host
			}
			return modules
		}

		private fun hostedUnder(hosts: Map<String, String>, from: String, target: String): Boolean {
			var above: String? = from
			var steps = hosts.size + 1
			while (above != null && steps-- > 0) {
				if (above == target) return true
				above = hosts[above]
			}
			return above != null
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
