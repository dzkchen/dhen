package io.github.dzkchen.dhen.data.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReforgeLookupTest {
	@TempDir
	lateinit var home: Path

	@Test
	fun `a blacksmith reforge is found by the modifier the item's nbt carries`() {
		write("reforges", ConstantsFixture.BLACKSMITH_REFORGES)

		val constants = read()

		assertEquals("Light", constants.reforge("light")?.reforge)
		assertEquals("Beady Eyes!", constants.reforge("beady_eyes")?.reforge)
		assertEquals("Nice", constants.reforge("very_nice")?.reforge)
		assertNull(constants.reforge("nice"))
	}

	@Test
	fun `reforge lookup reads stones and blacksmith reforges alike`() {
		write("reforges", ConstantsFixture.BLACKSMITH_REFORGES)
		write("reforgestones", ConstantsFixture.REFORGE_STONES)

		val constants = read()

		assertEquals("SPIRIT_STONE", constants.reforge("spiritual")?.stone)
		assertEquals("", constants.reforge("light")?.stone)
		assertNull(constants.reforge("SPIRIT_STONE"))
	}

	@Test
	fun `a slashed item type lists every side of the slash`() {
		write("reforges", ConstantsFixture.BLACKSMITH_REFORGES)

		val types = read().reforge("light")?.itemTypes

		assertEquals(listOf("SWORD", "ROD"), types)
	}

	@Test
	fun `an item category reaches the reforge types spelled with a space`() {
		assertEquals(listOf("ROD", "FISHING_ROD"), reforgeTypes("FISHING_ROD"))
		assertEquals(listOf("SWORD"), reforgeTypes("SWORD"))
		assertTrue(reforgeTypes("").isEmpty())
	}

	@Test
	fun `a gauntlet takes both the sword and the pickaxe reforges`() {
		assertEquals(listOf("SWORD", "PICKAXE"), reforgeTypes("GAUNTLET"))
	}

	@Test
	fun `a short bow is reachable under the two-word category its lore carries`() {
		assertEquals(listOf("BOW"), reforgeTypes("SHORT_BOW"))
	}

	private fun read(): RepoConstants = RepoConstants.read(home.resolve("constants"))

	private fun write(name: String, body: String) {
		val constants = home.resolve("constants")
		Files.createDirectories(constants)
		Files.writeString(constants.resolve("$name.json"), body)
	}
}
