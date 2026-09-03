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
	fun `a curve tier the pet's own tree does not offset falls back to the tier the pet is`() {
		write("pets", ConstantsFixture.PETS)

		val constants = read()

		assertEquals(100, constants.petLevel("BINGO", "LEGENDARY", 99.0, "MYTHIC"))
		assertEquals(50, constants.petLevel("AMMONITE", "LEGENDARY", 99.0, "MYTHIC"))
	}

	@Test
	fun `a pet of a tier the repo does not offset is left at level one`() {
		write("pets", ConstantsFixture.PETS)

		assertEquals(1, read().petLevel("AMMONITE", "DIVINE", 9999.0))
	}

	@Test
	fun `pet progress exposes current and next thresholds percentage and overflow`() {
		write("pets", ConstantsFixture.PETS)
		val constants = read()

		val halfway = constants.petProgress("AMMONITE", "LEGENDARY", 50.5)
		assertEquals(51, halfway.level)
		assertEquals(50.0, halfway.currentLevelXp)
		assertEquals(51.0, halfway.nextLevelXp)
		assertEquals(50.0, halfway.percentage)
		assertEquals(0.0, halfway.overflowXp)

		val maxed = constants.petProgress("AMMONITE", "LEGENDARY", 149.0)
		assertEquals(100, maxed.level)
		assertEquals(100.0, maxed.percentage)
		assertEquals(50.0, maxed.overflowXp)
	}

	@Test
	fun `skill experience is spent one level at a time and stops at the cap the repo names`() {
		write("leveling", ConstantsFixture.LEVELING)

		val constants = read()

		assertEquals(0, constants.level("combat", 49.0))
		assertEquals(1, constants.level("combat", 50.0))
		assertEquals(4, constants.level("combat", 675.0))
		assertEquals(5, constants.level("combat", 9999.0))
		assertEquals(3, constants.level("farming", 9999.0))
		val widened = constants.maxLevel(LevelLadder.SKILL, "farming") + 2
		assertEquals(5, constants.level(LevelLadder.SKILL, 9999.0, "farming", widened))
	}

	@Test
	fun `runecrafting and social level off their own tables, and an unlisted skill caps at fifty`() {
		write("leveling", ConstantsFixture.LEVELING)

		val constants = read()

		assertEquals(2, constants.level("runecrafting", 150.0))
		assertEquals(1, constants.level("social", 70.0))
		assertEquals(50, constants.maxLevel(LevelLadder.SKILL, "hunting"))
	}

	@Test
	fun `slayer experience is a running total rather than a cost per level`() {
		write("leveling", ConstantsFixture.LEVELING)

		val constants = read()

		assertEquals(0, constants.level(LevelLadder.SLAYER, 4.0, "zombie"))
		assertEquals(1, constants.level(LevelLadder.SLAYER, 5.0, "zombie"))
		assertEquals(2, constants.level(LevelLadder.SLAYER, 100.0, "zombie"))
		assertEquals(3, constants.level(LevelLadder.SLAYER, 9999.0, "zombie"))
		assertEquals(3, constants.maxLevel(LevelLadder.SLAYER, "zombie"))
		assertEquals(2, constants.maxLevel(LevelLadder.SLAYER, "vampire"))
	}

	@Test
	fun `a skill whose own ladder is missing falls back to the shared one rather than reading zero`() {
		write("leveling", """{"leveling_xp": [50, 125, 200], "leveling_caps": {"runecrafting": 3}}""")

		val constants = read()

		assertEquals(2, constants.level("runecrafting", 200.0))
		assertEquals(2, constants.level("social", 200.0))
	}

	@Test
	fun `a hole in a levelling table keeps the levels above it in place rather than shifting them down`() {
		write("leveling", """{"leveling_xp": [50, null, 200], "leveling_caps": {"combat": 3}}""")

		val constants = read()

		assertEquals(0, constants.level("combat", 49.0))
		assertEquals(2, constants.level("combat", 50.0))
		assertEquals(3, constants.level("combat", 250.0))
	}

	@Test
	fun `heart of the mountain and heart of the forest level off the repo's own tables`() {
		write("leveling", ConstantsFixture.LEVELING)

		val constants = read()

		assertEquals(1, constants.level(LevelLadder.SKILL_TREE, 0.0, "HOTM"))
		assertEquals(2, constants.level(LevelLadder.SKILL_TREE, 100.0, "HOTM"))
		assertEquals(3, constants.level(LevelLadder.SKILL_TREE, 9999.0, "HOTM"))
		assertEquals(3, constants.maxLevel(LevelLadder.SKILL_TREE, "HOTM"))
		assertEquals(2, constants.maxLevel(LevelLadder.SKILL_TREE, "HOTF"))
	}

	@Test
	fun `a repo with no garden table reports no garden level and no milestone`() {
		val constants = read()

		assertEquals(0, constants.level(LevelLadder.GARDEN, 9999.0, ""))
		assertEquals(0, constants.maxLevel(LevelLadder.GARDEN, ""))
		assertEquals(0, constants.level(LevelLadder.CROP_MILESTONE, 9999.0, "WHEAT"))
	}

	@Test
	fun `a repo with no levelling table reports no level rather than a made-up one`() {
		val constants = read()

		assertEquals(0, constants.level("combat", 9999.0))
		assertEquals(0, constants.level(LevelLadder.SLAYER, 9999.0, "zombie"))
		assertEquals(0, constants.maxLevel(LevelLadder.SLAYER, "zombie"))
	}

	private fun RepoConstants.level(skill: String, experience: Double): Int =
		level(LevelLadder.SKILL, experience, skill)

	@Test
	fun `island warps read their names and every alias, lowercased`() {
		write("islands", ConstantsFixture.ISLANDS)

		assertEquals(listOf("hub", "crystals", "ch", "nucleus"), read().warps.toList())
	}

	@Test
	fun `sack contents read as one uppercase set across every sack`() {
		write("sacks", ConstantsFixture.SACKS)

		assertEquals(listOf("SUGAR_CANE", "INK_SACK-2", "BONE"), read().sackItemIds.toList())
	}

	@Test
	fun `a repo missing the warp and sack constants leaves both empty rather than broken`() {
		write("reforgestones", ConstantsFixture.REFORGE_STONES)

		val constants = read()

		assertTrue(constants.warps.isEmpty())
		assertTrue(constants.sackItemIds.isEmpty())
	}

	private fun read(): RepoConstants = RepoConstants.read(home.resolve("constants"))

	private fun write(name: String, body: String) {
		val constants = home.resolve("constants")
		Files.createDirectories(constants)
		Files.writeString(constants.resolve("$name.json"), body)
	}
}
