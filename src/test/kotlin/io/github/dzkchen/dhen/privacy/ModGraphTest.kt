package io.github.dzkchen.dhen.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModGraphTest {
	@Test
	fun `only the four reserved paths are vanilla channels`() {
		for (path in listOf("register", "unregister", "brand", "mco")) {
			assertTrue(ModGraph.vanillaChannel("minecraft", path), path)
		}
		assertFalse(ModGraph.vanillaChannel("minecraft", "fancymenu_bridge"))
		assertFalse(ModGraph.vanillaChannel("fabric", "register"))
	}

	@Test
	fun `the loader's own common namespace is recognised`() {
		assertTrue(ModGraph.loaderCommonChannel("c"))
		assertFalse(ModGraph.loaderCommonChannel("carpet"))
		assertFalse(ModGraph.loaderCommonChannel("minecraft"))
	}

	@Test
	fun `a namespace resolves through its container, then its aliases, then itself`() {
		val graph = graph(
			ids = listOf("carpet", "wardrobe"),
			provided = mapOf("wardrobe" to setOf("closet"))
		)

		assertEquals("carpet", graph.ownerOf("carpet", "sync"))
		assertEquals("wardrobe", graph.ownerOf("closet", "sync"))
		assertEquals("stranger", graph.ownerOf("stranger", "sync"))
	}

	@Test
	fun `a reserved minecraft channel has no owner and a masquerading one is attributed`() {
		val graph = graph(ids = listOf("carpet", "carpetextra", "ab"))

		assertNull(graph.ownerOf("minecraft", "register"))
		assertEquals("carpet", graph.ownerOf("minecraft", "carpet"))
		assertEquals("carpet", graph.ownerOf("minecraft", "carpet/sync"))
		assertEquals("carpetextra", graph.ownerOf("minecraft", "carpetextra-sync"))
		assertNull(graph.ownerOf("minecraft", "carpetsync"))
		assertNull(graph.ownerOf("minecraft", "ab_sync"))
		assertNull(graph.ownerOf("minecraft", "unrelated"))
	}

	@Test
	fun `the closure follows required dependencies but not the platform`() {
		val graph = graph(
			ids = listOf("seed", "library", "deep", "unrelated"),
			required = mapOf(
				"seed" to setOf("library", "minecraft", "fabricloader", "java", "absent"),
				"library" to setOf("deep")
			)
		)

		assertEquals(setOf("seed", "library", "deep"), graph.closureOf(setOf("seed")))
	}

	@Test
	fun `the closure terminates on a cycle`() {
		val graph = graph(
			ids = listOf("a", "b", "c"),
			required = mapOf("a" to setOf("b"), "b" to setOf("c"), "c" to setOf("a"))
		)

		assertEquals(setOf("a", "b", "c"), graph.closureOf(setOf("a")))
	}

	@Test
	fun `the closure walks into contained jars and up to the jar-in-jar host`() {
		val graph = graph(
			ids = listOf("umbrella", "umbrella-net", "umbrella-render", "sibling"),
			contained = mapOf("umbrella" to setOf("umbrella-net", "umbrella-render")),
			jarHost = mapOf("umbrella-net" to "umbrella", "umbrella-render" to "umbrella")
		)

		assertEquals(
			setOf("umbrella", "umbrella-net", "umbrella-render"),
			graph.closureOf(setOf("umbrella-net"))
		)
	}

	@Test
	fun `a seed namespace no installed mod claims stays in the closure`() {
		val graph = graph(ids = listOf("roughlyenoughitems"))

		assertEquals(setOf("rei"), graph.closureOf(setOf("rei")))
		assertEquals(emptySet<String>(), graph.closureOf(setOf("minecraft", "fabricloader", "java")))
	}

	@Test
	fun `an uninstalled dependency is not in the closure`() {
		val graph = graph(ids = listOf("seed"), required = mapOf("seed" to setOf("absent")))

		assertEquals(setOf("seed"), graph.closureOf(setOf("seed")))
	}

	@Test
	fun `a provided alias joins the closure with its provider`() {
		val graph = graph(
			ids = listOf("seed", "provider"),
			provided = mapOf("provider" to setOf("old-name")),
			required = mapOf("seed" to setOf("old-name"))
		)

		assertEquals(setOf("seed", "provider", "old-name"), graph.closureOf(setOf("seed")))
	}

	@Test
	fun `every implicit closure member names the seed that pulled it in`() {
		val graph = graph(
			ids = listOf("seed", "library", "deep", "other"),
			required = mapOf("seed" to setOf("library"), "library" to setOf("deep"))
		)
		val requiredBy = HashMap<String, String>()

		graph.closureOf(setOf("seed"), requiredBy)

		assertEquals(mapOf("library" to "seed", "deep" to "seed"), requiredBy)
	}

	@Test
	fun `a seed carries no depender even when another seed reaches it`() {
		val graph = graph(
			ids = listOf("first", "second", "shared"),
			required = mapOf("first" to setOf("second", "shared"), "second" to setOf("shared"))
		)
		val requiredBy = HashMap<String, String>()

		graph.closureOf(setOf("first", "second"), requiredBy)

		assertEquals(mapOf("shared" to "first"), requiredBy)
	}

	@Test
	fun `a jar-in-jar host and a provided alias are attributed to the original seed`() {
		val graph = graph(
			ids = listOf("umbrella", "umbrella-net", "sibling"),
			provided = mapOf("umbrella" to setOf("old-umbrella")),
			contained = mapOf("umbrella" to setOf("umbrella-net")),
			jarHost = mapOf("umbrella-net" to "umbrella")
		)
		val requiredBy = HashMap<String, String>()

		graph.closureOf(setOf("umbrella-net"), requiredBy)

		assertEquals(
			mapOf("umbrella" to "umbrella-net", "old-umbrella" to "umbrella-net"),
			requiredBy
		)
	}

	@Test
	fun `a namespace is inferred for an umbrella whose children share its dash prefix`() {
		val inferred = ModGraph.jarInJarNamespaces(
			listOf("fabric-api", "onechild", "mismatched"),
			mapOf(
				"fabric-api" to setOf("fabric-networking-api-v1", "fabric-rendering-v1", "loose"),
				"onechild" to setOf("onechild-net"),
				"mismatched" to setOf("other-a", "other-b")
			)
		)

		assertEquals(mapOf("fabric" to "fabric-api"), inferred)
	}

	private fun graph(
		ids: List<String>,
		provided: Map<String, Set<String>> = emptyMap(),
		required: Map<String, Set<String>> = emptyMap(),
		contained: Map<String, Set<String>> = emptyMap(),
		jarHost: Map<String, String> = emptyMap()
	): ModGraph {
		val canonicalIds = HashMap<String, String>()
		for ((id, aliases) in provided) for (alias in aliases) canonicalIds[alias] = id
		for (id in ids) canonicalIds[id] = id
		return ModGraph(ids, canonicalIds, provided, required, contained, jarHost)
	}
}
