package io.github.dzkchen.dhen.data.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RepoConstantsTest {
	@TempDir
	lateinit var home: Path

	@Test
	fun `a repo without a constants folder reads as empty rather than broken`() {
		assertSame(RepoConstants.EMPTY, RepoConstants.read(home.resolve("constants")))
	}

	@Test
	fun `a constant that is not valid json leaves the rest of the constants readable`() {
		write("reforgestones", "{ this is not json")
		write("essencecosts", ConstantsFixture.ESSENCE_COSTS)

		val constants = read()

		assertEquals(0, constants.reforgeStoneCount)
		assertEquals(3, constants.starredItemCount)
	}

	@Test
	fun `a reforge stone is keyed by the modifier the item carries`() {
		write("reforgestones", ConstantsFixture.REFORGE_STONES)

		val constants = read()

		assertEquals("SPIRIT_STONE", constants.reforgeStone("spiritual")?.stone)
		assertEquals(75000L, constants.reforgeStone("spiritual")?.costs?.get("EPIC"))
		assertNull(constants.reforgeStone("SPIRIT_STONE"))
	}

	@Test
	fun `a reforge stone that names no modifier takes one from its reforge name`() {
		write("reforgestones", ConstantsFixture.REFORGE_STONES)

		assertEquals("BEADY_EYES", read().reforgeStone("beady_eyes")?.stone)
	}

	@Test
	fun `star tiers keep their order and split coins out of their materials`() {
		write("essencecosts", ConstantsFixture.ESSENCE_COSTS)

		val tiers = read().starTiers("HYPERION")

		assertEquals(5, tiers.size)
		assertEquals("WITHER", tiers[0].essence)
		assertEquals(listOf(150, 300, 500, 900, 1500), tiers.map { it.essenceAmount })
		assertTrue(tiers[0].materials.isEmpty())
		assertEquals(mapOf("SKYBLOCK_COIN" to 25000), tiers[4].materials)
	}

	@Test
	fun `a gemstone slot lists what unlocking it costs`() {
		write("gemstonecosts", ConstantsFixture.GEMSTONE_COSTS)

		val constants = read()

		assertEquals(mapOf("FINE_AMBER_GEM" to 20, "SKYBLOCK_COIN" to 250000), constants.gemstoneSlotCost("HYPERION", "COMBAT_0"))
		assertTrue(constants.gemstoneSlotCost("HYPERION", "COMBAT_9").isEmpty())
		assertTrue(constants.gemstoneSlotCost("MANDRAA", "COMBAT_0").isEmpty())
	}

	@Test
	fun `a pet levels off its own tree and stops at its own maximum`() {
		write("pets", ConstantsFixture.PETS)

		val constants = read()

		assertEquals(1, constants.petLevel("AMMONITE", "LEGENDARY", 0.0))
		assertEquals(99, constants.petLevel("AMMONITE", "LEGENDARY", 98.0))
		assertEquals(100, constants.petLevel("AMMONITE", "LEGENDARY", 99.0))
		assertEquals(100, constants.petLevel("AMMONITE", "LEGENDARY", 9999.0))
		assertEquals(200, constants.petLevel("GOLDEN_DRAGON", "LEGENDARY", 299.0))
		assertEquals(100, constants.petLevel("GOLDEN_DRAGON", "LEGENDARY", 99.0))
	}

	@Test
	fun `a pet of a tier the repo does not offset is left at level one`() {
		write("pets", ConstantsFixture.PETS)

		assertEquals(1, read().petLevel("AMMONITE", "DIVINE", 9999.0))
	}

	private fun read(): RepoConstants = RepoConstants.read(home.resolve("constants"))

	private fun write(name: String, body: String) {
		val constants = home.resolve("constants")
		Files.createDirectories(constants)
		Files.writeString(constants.resolve("$name.json"), body)
	}
}
