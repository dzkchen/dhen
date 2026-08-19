package io.github.dzkchen.dhen.data.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ItemCatalogTest {
	@TempDir
	lateinit var items: Path

	@Test
	fun `an item is read by its SkyBlock id with the fields the repo carries`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		val item = read().item("ASPECT_OF_THE_END")

		assertEquals("ASPECT_OF_THE_END", item?.id)
		assertEquals("minecraft:diamond_sword", item?.itemId)
		assertEquals("§5Aspect of the End", item?.displayName)
		assertEquals(0, item?.damage)
		assertEquals(listOf("§7Gear Score: §d123", "§5§lEPIC SWORD"), item?.lore)
	}

	@Test
	fun `an id resolves whatever case it is asked in`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		assertEquals("ASPECT_OF_THE_END", read().item("aspect_of_the_end")?.id)
	}

	@Test
	fun `a display name resolves back to its id without colour codes or case`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		assertEquals("ASPECT_OF_THE_END", read().idFor("Aspect of the End"))
		assertEquals("ASPECT_OF_THE_END", read().idFor("§5aspect of the end"))
	}

	@Test
	fun `an unknown id and an unknown name both answer nothing`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		assertNull(read().item("NOT_AN_ITEM"))
		assertNull(read().idFor("Not An Item"))
	}

	@Test
	fun `two items sharing a display name leave the first one holding the name`() {
		write("A_CLOAK.json", item("A_CLOAK", "§fCloak"))
		write("B_CLOAK.json", item("B_CLOAK", "§fCloak"))

		val catalog = read()

		assertEquals("A_CLOAK", catalog.idFor("Cloak"))
		assertEquals(2, catalog.size)
	}

	@Test
	fun `an item with no internal name falls back to its file name`() {
		write("ENCHANTED_BREAD.json", "{\"itemid\":\"minecraft:bread\",\"displayname\":\"§aEnchanted Bread\"}")

		assertEquals("ENCHANTED_BREAD", read().item("ENCHANTED_BREAD")?.id)
	}

	@Test
	fun `a broken file is skipped and the rest of the repo still reads`() {
		write("BROKEN.json", "{ not json")
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		val catalog = read()

		assertEquals(1, catalog.size)
		assertEquals(setOf("ASPECT_OF_THE_END"), catalog.ids)
	}

	@Test
	fun `anything that is not a json file is ignored`() {
		write("README.md", "not an item")
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		assertEquals(1, read().size)
	}

	@Test
	fun `a repo that was never downloaded reads as an empty catalog`() {
		assertEquals(0, ItemCatalog.read(items.resolve("missing")).size)
	}

	private fun read(): ItemCatalog = ItemCatalog.read(items)

	private fun write(name: String, content: String) {
		Files.writeString(items.resolve(name), content)
	}

	private fun item(id: String, displayName: String): String =
		"{\"itemid\":\"minecraft:skull\",\"displayname\":\"$displayName\",\"internalname\":\"$id\"}"

	private val aspectOfTheEnd = """
		{
			"itemid": "minecraft:diamond_sword",
			"displayname": "§5Aspect of the End",
			"nbttag": "{ExtraAttributes:{id:\"ASPECT_OF_THE_END\"}}",
			"damage": 0,
			"lore": ["§7Gear Score: §d123", "§5§lEPIC SWORD"],
			"internalname": "ASPECT_OF_THE_END",
			"crafttext": "Requires Combat Skill Level V",
			"modver": "2.1.0-REL"
		}
	""".trimIndent()
}
