package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.item.ItemFixture
import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ItemCatalogTest {
	@TempDir
	lateinit var root: Path

	private val items: Path get() = root.resolve("items")

	@BeforeEach
	fun createRepo() {
		Files.createDirectories(items)
	}

	@Test
	fun `an item is read by its SkyBlock id with the fields the repo carries`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		val item = read().item("ASPECT_OF_THE_END")

		assertEquals("ASPECT_OF_THE_END", item?.id)
		assertEquals("minecraft:diamond_sword", item?.itemId)
		assertEquals("§5Aspect of the End", item?.displayName)
		assertEquals(0, item?.damage)
		assertEquals(listOf("§7Gear Score: §d123", "", "§5§lEPIC SWORD"), item?.lore)
		assertEquals(listOf("https://wiki.hypixel.net/Aspect_of_the_End"), item?.info)
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
	fun `a legacy item id and damage pair become the modern item they flattened into`() {
		write("RED_WOOL.json", """{"internalname":"RED_WOOL","itemid":"minecraft:wool","damage":14,"displayname":"§cRed Wool"}""")

		val stack = read().stack("RED_WOOL")

		assertEquals("minecraft:red_wool", stack?.item?.let(BuiltInRegistries.ITEM::getKey).toString())
	}

	@Test
	fun `an items nbt reaches the stack as custom data lifted out of ExtraAttributes`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		val data = read().stack("ASPECT_OF_THE_END")?.get(DataComponents.CUSTOM_DATA)

		assertEquals("ASPECT_OF_THE_END", data?.copyTag()?.getStringOr("id", ""))
	}

	@Test
	fun `an items display name and lore come from the repo, not from its legacy nbt`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		val stack = read().stack("ASPECT_OF_THE_END")

		assertEquals("Aspect of the End", stack?.get(DataComponents.CUSTOM_NAME)?.string)
		assertEquals(3, stack?.get(DataComponents.LORE)?.lines()?.size)
	}

	@Test
	fun `an item whose nbt cannot be read still reaches the catalog as a named barrier`() {
		write("BAD_NBT.json", """{"internalname":"BAD_NBT","itemid":"not:a:real:item","displayname":"§cBroken"}""")

		val stack = read().stack("BAD_NBT")

		assertTrue(stack?.`is`(Items.BARRIER) == true)
		assertEquals("BAD_NBT", stack?.get(DataComponents.CUSTOM_NAME)?.string)
	}

	@Test
	fun `a pets level and stat placeholders are filled in from the repo pet numbers`() {
		Files.createDirectories(root.resolve("constants"))
		Files.writeString(root.resolve("constants/petnums.json"), petNumbers)
		write("BAT;4.json", batPet)

		val stack = read().stack("BAT;4")

		assertEquals("Bat 1 ➡ 100", stack?.get(DataComponents.CUSTOM_NAME)?.string)
		assertEquals(
			listOf("Strength: 5 ➡ 62.5", "Cooldown: 2 ➡ 40"),
			stack?.get(DataComponents.LORE)?.lines()?.map { it.string }
		)
	}

	@Test
	fun `every enchanted book answers to one custom data id so they do not read as thousands`() {
		write(
			"ULTIMATE_WISE;5.json",
			"""{"internalname":"ULTIMATE_WISE;5","itemid":"minecraft:enchanted_book","displayname":"§dUltimate Wise V"}"""
		)

		val data = read().stack("ULTIMATE_WISE;5")?.get(DataComponents.CUSTOM_DATA)

		assertEquals("ENCHANTED_BOOK", data?.copyTag()?.getStringOr("id", ""))
	}

	@Test
	fun `an overlay in an older directory still patches, because the directories do not repeat ids`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		write("A_CLOAK.json", item("A_CLOAK", "§fCloak"))
		overlay(4325, "ASPECT_OF_THE_END", "minecraft:diamond_sword", "old_model")
		overlay(SharedConstants.getCurrentVersion().dataVersion().version(), "A_CLOAK", "minecraft:player_head", "current_model")

		val catalog = read()

		assertEquals("minecraft:old_model", catalog.stack("ASPECT_OF_THE_END")?.get(DataComponents.ITEM_MODEL)?.toString())
		assertEquals("minecraft:current_model", catalog.stack("A_CLOAK")?.get(DataComponents.ITEM_MODEL)?.toString())
	}

	@Test
	fun `where two directories do name one item the newest the client can read wins`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		overlay(4325, "ASPECT_OF_THE_END", "minecraft:diamond_sword", "old_model")
		overlay(SharedConstants.getCurrentVersion().dataVersion().version(), "ASPECT_OF_THE_END", "minecraft:diamond_sword", "current_model")
		overlay(Int.MAX_VALUE, "ASPECT_OF_THE_END", "minecraft:diamond_sword", "future_model")

		val model = read().stack("ASPECT_OF_THE_END")?.get(DataComponents.ITEM_MODEL)

		assertEquals("minecraft:current_model", model?.toString())
	}

	@Test
	fun `an overlay never overwrites the name and lore the repo item carries`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		overlay(SharedConstants.getCurrentVersion().dataVersion().version(), "ASPECT_OF_THE_END", "minecraft:diamond_sword", "current_model")

		assertEquals("Aspect of the End", read().stack("ASPECT_OF_THE_END")?.get(DataComponents.CUSTOM_NAME)?.string)
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
		write("ENCHANTED_BREAD.json", """{"itemid":"minecraft:bread","displayname":"§aEnchanted Bread"}""")

		assertEquals("ENCHANTED_BREAD", read().item("ENCHANTED_BREAD")?.id)
	}

	@Test
	fun `one broken file among many is skipped and the rest of the repo still reads`() {
		write("BROKEN.json", "{ not json")
		filler(20)

		assertEquals(20, read().size)
	}

	@Test
	fun `a repo where the files stopped parsing is refused whole rather than half published`() {
		repeat(3) { write("BROKEN$it.json", "{ not json") }
		filler(20)

		assertEquals(0, read().size)
	}

	@Test
	fun `anything that is not a json file is ignored`() {
		write("README.md", "not an item")
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		assertEquals(1, read().size)
	}

	@Test
	fun `a crafting recipe keeps its nine slots in grid order and what it makes`() {
		write("ENCHANTED_LAPIS_LAZULI.json", enchantedLapis)

		val recipe = read().item("ENCHANTED_LAPIS_LAZULI")?.recipes?.single()

		assertEquals(RecipeKind.CRAFTING, recipe?.kind)
		assertEquals(9, recipe?.ingredients?.size)
		assertEquals(listOf("", "INK_SACK-4", ""), recipe?.ingredients?.take(3)?.map { it.id })
		assertEquals(32, recipe?.ingredients?.get(1)?.count)
		assertEquals("ENCHANTED_LAPIS_LAZULI", recipe?.output?.id)
		assertEquals(1, recipe?.output?.count)
	}

	@Test
	fun `an ingredient written without an amount counts as one`() {
		write("ONE_OF_EACH.json", """{"internalname":"ONE_OF_EACH","recipe":{"A1":"STICK","A2":""}}""")

		val recipe = read().item("ONE_OF_EACH")?.recipes?.single()

		assertEquals(listOf("STICK" to 1), recipe?.ingredients?.filter { it.present }?.map { it.id to it.count })
	}

	@Test
	fun `a forge recipe keeps an ingredient count no stack could hold and how long it takes`() {
		write("REFINED_MITHRIL.json", forgeRecipe)

		val recipe = read().item("REFINED_MITHRIL")?.recipes?.single()

		assertEquals(RecipeKind.FORGE, recipe?.kind)
		assertEquals(listOf("ENCHANTED_MITHRIL" to 160), recipe?.ingredients?.map { it.id to it.count })
		assertEquals(21600, recipe?.seconds)
		assertEquals("REFINED_MITHRIL", recipe?.output?.id)
	}

	@Test
	fun `a shop recipe reads both cost shapes and remembers which npc sells it`() {
		write("ADVENTURER.json", npcShop)

		val recipes = read().item("ADVENTURER")?.recipes

		assertEquals(listOf(RecipeKind.NPC_SHOP, RecipeKind.NPC_SHOP), recipes?.map { it.kind })
		assertEquals(listOf("SKYBLOCK_COIN" to 12), recipes?.first()?.ingredients?.map { it.id to it.count })
		assertEquals(listOf("GLACITE" to 6), recipes?.last()?.ingredients?.map { it.id to it.count })
		assertEquals("ADVENTURER", recipes?.first()?.owner)
		assertEquals("ROTTEN_FLESH", recipes?.first()?.output?.id)
	}

	@Test
	fun `a kat upgrade costs the pet, its materials and the coins together`() {
		write("KAT_FLYING_FISH.json", katUpgrade)

		val recipe = read().item("KAT_FLYING_FISH")?.recipes?.single()

		assertEquals(RecipeKind.KAT_UPGRADE, recipe?.kind)
		assertEquals(
			listOf("FLYING_FISH;3" to 1, "ENCHANTED_LAPIS_BLOCK" to 8, "SKYBLOCK_COIN" to 1000000),
			recipe?.ingredients?.map { it.id to it.count }
		)
		assertEquals("FLYING_FISH;4", recipe?.output?.id)
		assertEquals(7200, recipe?.seconds)
	}

	@Test
	fun `an ingredient answers which recipes consume it as well as which make it`() {
		write("ENCHANTED_LAPIS_LAZULI.json", enchantedLapis)
		write("INK_SACK-4.json", item("INK_SACK-4", "§9Lapis Lazuli"))

		val catalog = read()

		assertEquals(0, catalog.item("INK_SACK-4")?.recipes?.size)
		assertEquals(listOf("ENCHANTED_LAPIS_LAZULI"), catalog.usages("INK_SACK-4").map { it.owner })
		assertTrue(catalog.usages("ENCHANTED_LAPIS_LAZULI").isEmpty())
		assertEquals(listOf("ENCHANTED_LAPIS_LAZULI"), catalog.recipesFor("ENCHANTED_LAPIS_LAZULI").map { it.owner })
	}

	@Test
	fun `a recipe declared in one items file is found from the item it actually makes`() {
		write("ADVENTURER.json", npcShop)
		write("ROTTEN_FLESH.json", item("ROTTEN_FLESH", "§fRotten Flesh"))

		val catalog = read()

		assertEquals(emptyList<ItemRecipe>(), catalog.item("ROTTEN_FLESH")?.recipes)
		assertEquals(listOf("ADVENTURER"), catalog.recipesFor("ROTTEN_FLESH").map { it.owner })
		assertEquals(RecipeKind.NPC_SHOP, catalog.recipesFor("ROTTEN_FLESH").single().kind)
	}

	@Test
	fun `a recipe that overrides its output is found from the override, not from its file`() {
		write(
			"WEIRD_BOX.json",
			"""{"internalname":"WEIRD_BOX","recipe":{"A1":"STICK:2","overrideOutputId":"SUPER_STICK","count":4}}"""
		)

		val catalog = read()

		assertTrue(catalog.recipesFor("WEIRD_BOX").isEmpty())
		assertEquals(4, catalog.recipesFor("SUPER_STICK").single().output.count)
	}

	@Test
	fun `a slot repeated across the grid lists its recipe once, not nine times`() {
		write("ENCHANTED_LAPIS_LAZULI.json", enchantedLapis)

		assertEquals(1, read().usages("INK_SACK-4").size)
	}

	@Test
	fun `an item nobody crafts carries no recipe`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		assertEquals(emptyList<ItemRecipe>(), read().item("ASPECT_OF_THE_END")?.recipes)
	}

	@Test
	fun `coins resolve to a stack even though no repo item describes them`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		val catalog = read()

		val coins = catalog.ingredientStack("SKYBLOCK_COIN")

		assertTrue(coins.`is`(Items.GOLD_NUGGET))
		assertEquals("Skyblock Coins", coins.get(DataComponents.ITEM_NAME)?.string)
		assertSame(coins, catalog.ingredientStack("SKYBLOCK_COIN"))
	}

	@Test
	fun `an ingredient the repo has never heard of resolves to a barrier naming it`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		val unknown = read().ingredientStack("NOT_AN_ITEM")

		assertTrue(unknown.`is`(Items.BARRIER))
		assertEquals("NOT_AN_ITEM", unknown.get(DataComponents.CUSTOM_NAME)?.string)
	}

	@Test
	fun `variants of one item sort next to each other rather than by file name`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		write("BAT;0.json", item("BAT;0", "§fBat"))
		write("BAT;4.json", item("BAT;4", "§6Bat"))
		write("BAT_PERSON.json", item("BAT_PERSON", "§fBat Person"))

		assertEquals(listOf("ASPECT_OF_THE_END", "BAT;0", "BAT;4", "BAT_PERSON"), read().ids.toList())
	}

	@Test
	fun `a repo that was never downloaded reads as an empty catalog`() {
		assertEquals(0, ItemCatalog.read(root.resolve("missing")).size)
	}

	@Test
	fun `a compiled catalog is read back instead of the json and only for the commit that built it`() {
		write("ENCHANTED_LAPIS_LAZULI.json", enchantedLapis)
		val cache = root.resolve("repo.catalog")

		assertEquals(1, ItemCatalog.read(root, cache, "abc123").size)
		assertTrue(Files.isRegularFile(cache))

		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)

		assertEquals(1, ItemCatalog.read(root, cache, "abc123").size)
		assertEquals(2, ItemCatalog.read(root, cache, "def456").size)
	}

	@Test
	fun `a compiled catalog carries every recipe field back across a restart`() {
		write("KAT_FLYING_FISH.json", katUpgrade)
		val cache = root.resolve("repo.catalog")
		ItemCatalog.read(root, cache, "abc123")
		Files.walk(items).use { listing -> listing.filter(Files::isRegularFile).forEach(Files::delete) }

		val recipe = ItemCatalog.read(root, cache, "abc123").item("KAT_FLYING_FISH")?.recipes?.single()

		assertEquals(RecipeKind.KAT_UPGRADE, recipe?.kind)
		assertEquals("KAT_FLYING_FISH", recipe?.owner)
		assertEquals(7200, recipe?.seconds)
		assertEquals("FLYING_FISH;4", recipe?.output?.id)
		assertEquals(1000000, recipe?.ingredients?.last()?.count)
	}

	@Test
	fun `a compiled catalog that was truncated is thrown away and the json parsed again`() {
		write("ENCHANTED_LAPIS_LAZULI.json", enchantedLapis)
		val cache = root.resolve("repo.catalog")
		ItemCatalog.read(root, cache, "abc123")
		Files.write(cache, Files.readAllBytes(cache).copyOfRange(0, 12))

		assertEquals(1, ItemCatalog.read(root, cache, "abc123").size)
		assertNotNull(CatalogCache.read(cache, "abc123"))
	}

	@Test
	fun `legacy list indices are stripped so the nbt reads, and quoted text keeps its own`() {
		assertEquals("""{a:["x","y"]}""", withoutListIndices("""{a:[0:"x",1:"y"]}"""))
		assertEquals("""{a:"0:x"}""", withoutListIndices("""{a:"0:x"}"""))
		assertEquals("""{a:[I;1,2]}""", withoutListIndices("""{a:[I;1,2]}"""))
		assertEquals("""{a:"b\"[0:c"}""", withoutListIndices("""{a:"b\"[0:c"}"""))
	}

	private fun read(): ItemCatalog = ItemCatalog.read(root)

	private fun write(name: String, content: String) {
		Files.writeString(items.resolve(name), content)
	}

	private fun filler(count: Int) {
		repeat(count) { write("FILLER$it.json", item("FILLER$it", "§fFiller $it")) }
	}

	private fun overlay(dataVersion: Int, id: String, itemId: String, model: String) {
		val directory = root.resolve("itemsOverlay/$dataVersion")
		Files.createDirectories(directory)
		Files.writeString(
			directory.resolve("$id.snbt"),
			"""{id:"$itemId",count:1,components:{"minecraft:item_model":"minecraft:$model"}}"""
		)
	}

	private fun item(id: String, displayName: String): String =
		"""{"itemid":"minecraft:skull","damage":3,"displayname":"$displayName","internalname":"$id"}"""

	private val enchantedLapis = """
		{
			"internalname": "ENCHANTED_LAPIS_LAZULI",
			"displayname": "§aEnchanted Lapis Lazuli",
			"recipes": [
				{
					"type": "crafting",
					"A1": "", "A2": "INK_SACK-4:32", "A3": "",
					"B1": "INK_SACK-4:32", "B2": "INK_SACK-4:32", "B3": "INK_SACK-4:32",
					"C1": "", "C2": "INK_SACK-4:32", "C3": "",
					"count": 1
				}
			]
		}
	""".trimIndent()

	private val forgeRecipe = """
		{
			"internalname": "REFINED_MITHRIL",
			"displayname": "§9Refined Mithril",
			"recipes": [{"type": "forge", "inputs": ["ENCHANTED_MITHRIL:160"], "duration": 21600, "count": 1}]
		}
	""".trimIndent()

	private val npcShop = """
		{
			"internalname": "ADVENTURER",
			"displayname": "§aAdventurer",
			"recipes": [
				{"type": "npc_shop", "cost": ["SKYBLOCK_COIN:12"], "result": "ROTTEN_FLESH:1"},
				{"type": "npc_shop", "cost": [{"item": "GLACITE:2", "cost": 3}], "result": "GLACITE_JEWEL"}
			]
		}
	""".trimIndent()

	private val katUpgrade = """
		{
			"internalname": "KAT_FLYING_FISH",
			"displayname": "§aKat",
			"recipes": [{
				"type": "katgrade",
				"coins": 1000000,
				"time": 7200,
				"input": "FLYING_FISH;3",
				"output": "FLYING_FISH;4",
				"items": ["ENCHANTED_LAPIS_BLOCK:8"]
			}]
		}
	""".trimIndent()

	private val batPet = """
		{
			"internalname": "BAT;4",
			"itemid": "minecraft:skull",
			"damage": 3,
			"displayname": "§6Bat {LVL}",
			"lore": ["§7Strength: §a{STRENGTH}", "§7Cooldown: §a{0}"]
		}
	""".trimIndent()

	private val petNumbers = """
		{
			"BAT": {
				"LEGENDARY": {
					"1": {"statNums": {"STRENGTH": 5.0}, "otherNums": [2.0]},
					"100": {"statNums": {"STRENGTH": 62.5}, "otherNums": [40.0]}
				}
			}
		}
	""".trimIndent()

	private val aspectOfTheEnd = """
		{
			"itemid": "minecraft:diamond_sword",
			"displayname": "§5Aspect of the End",
			"nbttag": "{ExtraAttributes:{id:\"ASPECT_OF_THE_END\"},display:{Lore:[0:\"§7old\"],Name:\"§5old\"}}",
			"damage": 0,
			"lore": ["§7Gear Score: §d123", "", "§5§lEPIC SWORD"],
			"internalname": "ASPECT_OF_THE_END",
			"crafttext": "Requires Combat Skill Level V",
			"infoType": "WIKI_URL",
			"info": ["https://wiki.hypixel.net/Aspect_of_the_End"],
			"modver": "2.1.0-REL"
		}
	""".trimIndent()

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
