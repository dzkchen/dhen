package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.data.item.ItemFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ExtraRecipeKindsTest {
	@TempDir
	lateinit var root: Path

	private val items: Path get() = root.resolve("items")

	private val constants: Path get() = root.resolve("constants")

	@BeforeEach
	fun createRepo() {
		Files.createDirectories(items)
		Files.createDirectories(constants)
	}

	@Test
	fun `a drop table becomes one recipe per drop, each carrying its own chance`() {
		write("AGARIMOO_MONSTER.json", agarimoo)

		val catalog = read()
		val drops = catalog.item("AGARIMOO_MONSTER")?.recipes

		assertEquals(listOf(RecipeKind.MOB_DROP, RecipeKind.MOB_DROP), drops?.map { it.kind })
		assertEquals(listOf("RAW_BEEF", "HORRIBLE_HIDE"), drops?.map { it.output.id })
		assertEquals(listOf(4, 1), drops?.map { it.output.count })
		assertEquals(listOf("100%", "5%"), drops?.map { (it.detail as MobDrop).chance })
		assertEquals("§cAgarimoo", (drops?.first()?.detail as MobDrop).mob)
	}

	@Test
	fun `a drop is found from the item it drops and from the mob that drops it`() {
		write("AGARIMOO_MONSTER.json", agarimoo)

		val catalog = read()

		assertEquals(listOf("AGARIMOO_MONSTER"), catalog.recipesFor("HORRIBLE_HIDE").map { it.owner })
		assertEquals(2, catalog.usages("AGARIMOO_MONSTER").size)
	}

	@Test
	fun `a variable trade shows its lowest cost and keeps the whole range`() {
		write("BARTER.json", trades)

		val catalog = read()
		val variable = catalog.recipesFor("ANCIENT_CLAW").single()
		val fixed = catalog.recipesFor("GOLDEN_TOOTH").single()

		assertEquals(RecipeKind.TRADE, variable.kind)
		assertEquals(listOf("GOLD_INGOT" to 8), variable.ingredients.map { it.id to it.count })
		val range = variable.detail as TradeRange
		assertEquals(8 to 16, range.minimum to range.maximum)
		assertTrue(range.variable)
		assertEquals(listOf("ROTTEN_FLESH" to 32), fixed.ingredients.map { it.id to it.count })
		assertEquals(2, fixed.output.count)
	}

	@Test
	fun `an npc gets a card naming its island and coordinates`() {
		write("ADVENTURER_NPC.json", adventurerNpc)

		val card = read().recipesFor("ADVENTURER_NPC").single()

		assertEquals(RecipeKind.NPC_INFO, card.kind)
		val place = card.detail as NpcPlace
		assertEquals(Island.HUB, place.island)
		assertEquals(Triple(-4, 71, -75), Triple(place.x, place.y, place.z))
		assertTrue(place.located)
	}

	@Test
	fun `an item with no recipe data at all still answers with a wiki card`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		write("MYSTERY_ROCK.json", """{"internalname":"MYSTERY_ROCK","displayname":"§7Mystery Rock"}""")

		val catalog = read()
		val linked = catalog.infoCard("ASPECT_OF_THE_END").single()
		val unlinked = catalog.infoCard("MYSTERY_ROCK").single()

		val card = linked.detail as WikiCard
		assertEquals(RecipeKind.WIKI_INFO, linked.kind)
		assertEquals(listOf("https://wiki.hypixel.net/Aspect_of_the_End"), card.links)
		assertEquals("Requires Combat Skill Level V", card.requirement)
		assertTrue((unlinked.detail as WikiCard).links.single().contains("Mystery+Rock"))
	}

	@Test
	fun `an items info is only treated as a wiki link when the repo says it is one`() {
		write("PROSE.json", """{"internalname":"PROSE","displayname":"§7Prose","infoType":"PARENT","info":["see the parent"]}""")

		val card = read().infoCard("PROSE").single().detail as WikiCard

		assertTrue(card.links.single().startsWith(WikiLinks.ROOT))
	}

	@Test
	fun `an essence upgrade becomes one recipe per star, paying essence, materials and coins`() {
		write("HYPERION.json", item("HYPERION", "§dHyperion"))
		writeConstant("essencecosts", ConstantsFixture.ESSENCE_COSTS)

		val stars = read().recipesFor("HYPERION")

		assertEquals(5, stars.size)
		assertEquals(RecipeKind.ESSENCE_UPGRADE, stars.first().kind)
		assertEquals(listOf("ESSENCE_WITHER" to 150), stars.first().ingredients.map { it.id to it.count })
		assertEquals(1, (stars.first().detail as EssenceStar).star)
		assertEquals(
			listOf("ESSENCE_WITHER" to 1500, "SKYBLOCK_COIN" to 25000),
			stars.last().ingredients.map { it.id to it.count }
		)
	}

	@Test
	fun `a reforge finds every item of its type, whatever rarity or dungeon prefix the lore carries`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		write("LIVID_DAGGER.json", lividDagger)
		write("HYPERION.json", item("HYPERION", "§dHyperion"))
		writeConstant("reforgestones", swordStone)

		val catalog = read()

		assertEquals(listOf("Spiritual"), catalog.reforges("ASPECT_OF_THE_END").map { reforgeName(it) })
		assertEquals(listOf("Spiritual"), catalog.reforges("LIVID_DAGGER").map { reforgeName(it) })
		assertTrue(catalog.reforges("HYPERION").isEmpty())
	}

	@Test
	fun `a reforge listed against item ids matches those items and its stone lists it as a usage`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		write("SPIRIT_STONE.json", item("SPIRIT_STONE", "§5Spirit Stone"))
		writeConstant("reforgestones", namedStone)

		val catalog = read()
		val reforge = catalog.reforges("ASPECT_OF_THE_END").single()

		val card = reforge.detail as ReforgeCard
		assertEquals("SPIRIT_STONE", card.stone)
		assertEquals(listOf("EPIC", "LEGENDARY"), card.rarities)
		assertEquals(15.0, card.stats["EPIC"]?.get("STRENGTH"))
		assertEquals(listOf(RecipeKind.REFORGE), catalog.usages("SPIRIT_STONE").map { it.kind })
	}

	@Test
	fun `a blacksmith reforge carries an ability written once for every rarity`() {
		write("ASPECT_OF_THE_END.json", aspectOfTheEnd)
		writeConstant("reforges", blacksmith)

		val card = read().reforges("ASPECT_OF_THE_END").single().detail as ReforgeCard

		assertTrue(card.blacksmith)
		assertEquals("Grants +5 Strength", card.ability.values.single())
		assertEquals(1000L, card.costs["COMMON"])
	}

	@Test
	fun `the bundled garden mutations load with their grid, its ingredient counts and its coin cost`() {
		val mutation = GardenMutations.recipes.first { it.owner == "PHANTOMLEAF" }
		val plot = mutation.detail as MutationPlot

		assertEquals(RecipeKind.GARDEN_MUTATION, mutation.kind)
		assertEquals(3, plot.size)
		assertEquals("Soul Sand", plot.surface)
		assertTrue(plot.grown(1, 1))
		assertEquals("SHELLFRUIT", plot.plantedAt(0, 0))
		assertEquals(
			listOf("SHELLFRUIT" to 4, "CHORUS_FRUIT" to 4, "SKYBLOCK_COIN" to 3000000),
			mutation.ingredients.map { it.id to it.count }
		)
	}

	@Test
	fun `a mutation whose grid rows are shorter than its size still reads its cells where they are`() {
		val plot = GardenMutations.recipes.first { it.owner == "SHELLFRUIT" }.detail as MutationPlot

		assertEquals(2, plot.size)
		assertEquals("BLASTBERRY", plot.plantedAt(0, 0))
		assertTrue(plot.grown(1, 0))
		assertFalse(plot.grown(0, 1))
		assertEquals("", plot.plantedAt(1, 1))
	}

	@Test
	fun `a mutation is reachable from the crop it makes and from every crop it plants`() {
		write("PHANTOMLEAF.json", item("PHANTOMLEAF", "§6Phantomleaf"))
		write("SHELLFRUIT.json", item("SHELLFRUIT", "§aShellfruit"))

		val catalog = read()

		assertEquals(listOf(RecipeKind.GARDEN_MUTATION), catalog.recipesFor("PHANTOMLEAF").map { it.kind })
		assertTrue(catalog.usages("SHELLFRUIT").any { it.owner == "PHANTOMLEAF" })
	}

	@Test
	fun `a fusion pair resolves both inputs and takes its output quantity from the group it sits in`() {
		val fusions = ShardFusions.catalogue(fusionData, shardConstants())
		val made = fusions.fusionsFor("ATTRIBUTE_SHARD_TERRA")

		assertEquals(3, fusions.shards)
		assertEquals(2, made.size)
		assertEquals(RecipeKind.SHARD_FUSION, made.first().kind)
		assertEquals(
			listOf("ATTRIBUTE_SHARD_FLARE" to 2, "ATTRIBUTE_SHARD_MIST" to 1),
			made.first().ingredients.map { it.id to it.count }
		)
		assertEquals(3, made.first().output.count)
		assertEquals(1, made.last().output.count)
	}

	@Test
	fun `a fusion joins on the bazaar name when the game shard id is unknown, and drops what neither resolves`() {
		val fusions = ShardFusions.catalogue(fusionData, shardConstants())

		assertEquals(2, fusions.pairs)
		assertTrue(fusions.fusionsFor("ATTRIBUTE_SHARD_FLARE").isEmpty())
		assertEquals(2, fusions.fusionsWith("ATTRIBUTE_SHARD_FLARE").size)
		assertTrue(fusions.fusionsWith("ATTRIBUTE_SHARD_GHOST").isEmpty())
	}

	@Test
	fun `the compiled catalog remembers the drop chances, the trade range and where an npc stands`() {
		write("AGARIMOO_MONSTER.json", agarimoo)
		write("BARTER.json", trades)
		write("ADVENTURER_NPC.json", adventurerNpc)
		val cache = root.resolve("catalog.bin")

		ItemCatalog.read(root, cache, "abc")
		val cached = ItemCatalog.read(root.resolve("gone"), cache, "abc")

		assertEquals("5%", (cached.recipesFor("HORRIBLE_HIDE").single().detail as MobDrop).chance)
		assertEquals(16, (cached.recipesFor("ANCIENT_CLAW").single().detail as TradeRange).maximum)
		assertEquals(Island.HUB, (cached.recipesFor("ADVENTURER_NPC").single().detail as NpcPlace).island)
		assertNotNull(cached.item("AGARIMOO_MONSTER"))
	}

	private fun reforgeName(recipe: ItemRecipe): String = (recipe.detail as ReforgeCard).reforge

	private fun read(): ItemCatalog =
		ItemCatalog.read(root, constants = RepoConstants.read(constants))

	private fun shardConstants(): RepoConstants {
		writeConstant("attribute_shards", attributeShards)
		return RepoConstants.read(constants)
	}

	private fun write(name: String, content: String) {
		Files.writeString(items.resolve(name), content)
	}

	private fun writeConstant(name: String, content: String) {
		Files.writeString(constants.resolve("$name.json"), content)
	}

	private fun item(id: String, displayName: String): String =
		"""{"internalname":"$id","displayname":"$displayName"}"""

	private val agarimoo = """
		{
			"internalname": "AGARIMOO_MONSTER",
			"displayname": "§cAgarimoo",
			"recipes": [{
				"type": "drops",
				"name": "§cAgarimoo",
				"render": "Mooshroom",
				"drops": [
					{"id": "RAW_BEEF:4", "chance": "100%"},
					{"id": "HORRIBLE_HIDE", "chance": "5%"}
				]
			}]
		}
	""".trimIndent()

	private val trades = """
		{
			"internalname": "BARTER",
			"displayname": "§6Bartering",
			"recipes": [
				{"type": "trade", "cost": "GOLD_INGOT:1", "result": "ANCIENT_CLAW", "count": 1, "min": 8, "max": 16},
				{"type": "trade", "cost": "ROTTEN_FLESH:32", "result": "GOLDEN_TOOTH", "count": 2}
			]
		}
	""".trimIndent()

	private val adventurerNpc = """
		{
			"internalname": "ADVENTURER_NPC",
			"displayname": "§aAdventurer",
			"island": "hub",
			"x": -4,
			"y": 71,
			"z": -75
		}
	""".trimIndent()

	private val aspectOfTheEnd = """
		{
			"internalname": "ASPECT_OF_THE_END",
			"itemid": "minecraft:diamond_sword",
			"displayname": "§5Aspect of the End",
			"lore": ["§7Gear Score: §d123", "", "§5§lEPIC SWORD"],
			"crafttext": "Requires Combat Skill Level V",
			"infoType": "WIKI_URL",
			"info": ["https://wiki.hypixel.net/Aspect_of_the_End"]
		}
	""".trimIndent()

	private val lividDagger = """
		{
			"internalname": "LIVID_DAGGER",
			"itemid": "minecraft:iron_sword",
			"displayname": "§dLivid Dagger",
			"lore": ["§7Damage: §c+210", "", "§d§lMYTHIC DUNGEON SWORD"]
		}
	""".trimIndent()

	private val swordStone = """
		{
		  "SPIRIT_STONE": {
		    "internalName": "SPIRIT_STONE",
		    "reforgeName": "Spiritual",
		    "nbtModifier": "spiritual",
		    "itemTypes": "SWORD",
		    "requiredRarities": ["epic", "legendary"]
		  }
		}
	"""

	private val namedStone = """
		{
		  "SPIRIT_STONE": {
		    "internalName": "SPIRIT_STONE",
		    "reforgeName": "Spiritual",
		    "itemTypes": {"internalName": ["ASPECT_OF_THE_END", "HYPERION"]},
		    "requiredRarities": ["epic", "legendary"],
		    "reforgeStats": {"EPIC": {"STRENGTH": 15}, "LEGENDARY": {"STRENGTH": 20}}
		  }
		}
	"""

	private val blacksmith = """
		{
		  "Heroic": {
		    "reforgeName": "Heroic",
		    "itemTypes": "SWORD",
		    "reforgeAbility": "Grants +5 Strength",
		    "reforgeCosts": {"COMMON": 1000}
		  }
		}
	"""

	private val attributeShards = """
		{
		  "attributes": [
		    {"internalName": "ATTRIBUTE_SHARD_TERRA", "shardId": "E1", "bazaarName": "SHARD_TERRA"},
		    {"internalName": "ATTRIBUTE_SHARD_FLARE", "shardId": "", "bazaarName": "SHARD_FLARE"},
		    {"internalName": "ATTRIBUTE_SHARD_MIST", "shardId": "W3", "bazaarName": "SHARD_MIST"}
		  ]
		}
	"""

	private val fusionData = """
		{
		  "shards": {
		    "E1": {"name": "Terra", "rarity": "epic", "fuse_amount": 2, "internal_id": "SHARD_TERRA"},
		    "C7": {"name": "Flare", "rarity": "rare", "fuse_amount": 2, "internal_id": "SHARD_FLARE"},
		    "W3": {"name": "Mist", "rarity": "common", "fuse_amount": 1, "internal_id": "SHARD_MIST"},
		    "Z9": {"name": "Ghost", "rarity": "rare", "fuse_amount": 4, "internal_id": "SHARD_GHOST"}
		  },
		  "recipes": {
		    "E1": {
		      "3": [["C7", "W3"]],
		      "1": [["W3", "C7"], ["Z9", "W3"]]
		    },
		    "Q4": {"1": [["E1", "W3"]]}
		  }
		}
	"""

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = ItemFixture.bootstrap()
	}
}
