package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.Island
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.textOrNull
import io.github.dzkchen.dhen.util.texts
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asSequence

class SearchName internal constructor(val label: String, val lowercase: String, val id: String)

class RepoItem(
	val id: String,
	val itemId: String,
	val displayName: String,
	val damage: Int,
	val lore: List<String>,
	val nbttag: String,
	val info: List<String>,
	val recipes: List<ItemRecipe>,
	val infoType: String = "",
	val craftText: String = "",
	val slayerRequirement: String = "",
	val island: String = "",
	val x: Int = 0,
	val y: Int = 0,
	val z: Int = 0
) {
	val npc: Boolean get() = id.endsWith(NPC_SUFFIX)

	internal companion object {
		const val NPC_SUFFIX = "_NPC"
	}
}

class ItemCatalog private constructor(
	private val byId: Map<String, RepoItem>,
	private val byDisplayName: Map<String, String>,
	private val stacks: Map<String, ItemStack>,
	private val made: Map<String, List<ItemRecipe>>,
	private val usedIn: Map<String, List<ItemRecipe>>,
	private val reforgeIndex: ReforgeIndex,
	private val counts: IntArray
) {
	private val pseudo = ConcurrentHashMap<String, ItemStack>()
	private val fallback = ConcurrentHashMap<String, List<ItemRecipe>>()

	val size: Int get() = byId.size

	val ids: Set<String> get() = byId.keys

	fun item(id: String): RepoItem? = byId[id.uppercase(Locale.ROOT)]

	fun idFor(displayName: String): String? = byDisplayName[normalized(displayName)]

	fun stack(id: String): ItemStack? = stacks[id.uppercase(Locale.ROOT)]

	fun recipesFor(id: String): List<ItemRecipe> = made[id.uppercase(Locale.ROOT)].orEmpty()

	fun infoCard(id: String): List<ItemRecipe> {
		val key = id.uppercase(Locale.ROOT)
		val item = byId[key] ?: return emptyList()
		return fallback.computeIfAbsent(key) { listOf(wikiCard(item)) }
	}

	fun usages(id: String): List<ItemRecipe> = usedIn[id.uppercase(Locale.ROOT)].orEmpty()

	fun reforges(id: String): List<ItemRecipe> = byId[id.uppercase(Locale.ROOT)]?.let(reforgeIndex::matching).orEmpty()

	fun ingredientStack(id: String): ItemStack {
		val key = id.uppercase(Locale.ROOT)
		stacks[key]?.let { return it }
		return pseudo.computeIfAbsent(key) { if (it == SKYBLOCK_COIN) coinStack() else namedPlaceholder(it) }
	}

	fun recipeCount(kind: RecipeKind): Int = counts.getOrElse(kind.ordinal) { 0 }

	val searchNames: List<SearchName> by lazy {
		byId.values.mapNotNull { item ->
			val label = searchName(item.displayName)
			if (label.isEmpty()) null else SearchName(label, label.lowercase(Locale.ROOT), item.id)
		}
	}

	internal companion object {
		private const val JSON = ".json"
		private const val PET_LEVEL_PREFIX = "[Lvl {LVL}] "
		private const val WIKI_URL = "WIKI_URL"
		private const val ITEMS = "items"
		private const val CONSTANTS = "constants"
		private const val MAX_FAILURE_RATE = 0.05

		val EMPTY = ItemCatalog(
			emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), ReforgeIndex.EMPTY, IntArray(0)
		)

		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		private val VARIANT = Regex(".\\d+$")

		fun read(
			root: Path,
			cache: Path? = null,
			commit: String = "",
			constants: RepoConstants = RepoConstants.EMPTY
		): ItemCatalog {
			val parsed = cache?.let { CatalogCache.read(it, commit) } ?: compile(root, cache, commit)
			return if (parsed.isEmpty()) EMPTY else assemble(parsed, root, constants)
		}

		private fun compile(root: Path, cache: Path?, commit: String): List<RepoItem> {
			val items = root.resolve(ITEMS)
			if (!Files.isDirectory(items)) return emptyList()
			val parsed = ArrayList<RepoItem>()
			var attempts = 0
			Files.list(items).use { listing ->
				for (file in listing.asSequence()) {
					if (!file.fileName.toString().endsWith(JSON)) continue
					attempts++
					parse(file)?.let(parsed::add)
				}
			}
			val failures = attempts - parsed.size
			if (failures > attempts * MAX_FAILURE_RATE) {
				log.error(
					"Dhen refused the item repo: {} of {} items failed to parse, which reads as a repo format change",
					failures,
					attempts
				)
				return emptyList()
			}
			val family = HashMap<String, String>(parsed.size)
			for (item in parsed) family[item.id] = VARIANT.replace(item.id, "")
			parsed.sortWith(compareBy({ family.getValue(it.id) }, { it.id.length }, { it.id }))
			cache?.let { CatalogCache.write(it, commit, parsed) }
			return parsed
		}

		private fun assemble(parsed: List<RepoItem>, root: Path, constants: RepoConstants): ItemCatalog {
			val pets = PetNumbers.read(root.resolve(CONSTANTS))
			val overlays = StackOverlays.read(root)
			val byId = LinkedHashMap<String, RepoItem>(parsed.size)
			val byDisplayName = HashMap<String, String>(parsed.size)
			val stacks = HashMap<String, ItemStack>(parsed.size)
			val made = HashMap<String, MutableList<ItemRecipe>>()
			val usages = HashMap<String, MutableList<ItemRecipe>>()
			val counts = IntArray(RecipeKind.entries.size)
			for (item in parsed) {
				if (byId.put(item.id, item) != null) continue
				byDisplayName.putIfAbsent(normalized(item.displayName), item.id)
				stacks[item.id] = repoStack(item, pets).also { overlays.apply(item.id, it) }
				index(item.recipes, made, usages, counts)
				if (item.npc) index(listOf(npcCard(item)), made, usages, counts)
			}
			val reforgeIndex = ReforgeIndex.of(constants)
			index(essenceUpgrades(constants, byId.keys), made, usages, counts)
			index(GardenMutations.recipes, made, usages, counts)
			index(reforgeIndex.recipes, made, usages, counts)
			return ItemCatalog(byId, byDisplayName, stacks, made, usages, reforgeIndex, counts)
		}

		private fun npcCard(item: RepoItem): ItemRecipe = ItemRecipe(
			RecipeKind.NPC_INFO,
			item.id,
			emptyList(),
			ItemIngredient(item.id, 1),
			0,
			NpcPlace(Island.ofMode(item.island), item.island, item.x, item.y, item.z, wikiLinks(item))
		)

		private fun wikiLinks(item: RepoItem): List<String> =
			if (item.infoType == WIKI_URL) item.info else emptyList()

		private fun wikiCard(item: RepoItem): ItemRecipe {
			val links = wikiLinks(item).ifEmpty { listOf(WikiLinks.search(searchName(item.displayName))) }
			return ItemRecipe(
				RecipeKind.WIKI_INFO,
				item.id,
				emptyList(),
				ItemIngredient(item.id, 1),
				0,
				WikiCard(links, item.craftText, item.slayerRequirement)
			)
		}

		private fun index(
			recipes: List<ItemRecipe>,
			made: MutableMap<String, MutableList<ItemRecipe>>,
			usages: MutableMap<String, MutableList<ItemRecipe>>,
			counts: IntArray
		) {
			for (recipe in recipes) {
				counts[recipe.kind.ordinal]++
				if (recipe.output.present) made.getOrPut(recipe.output.id, ::ArrayList).add(recipe)
				val seen = HashSet<String>(recipe.ingredients.size)
				for (ingredient in recipe.ingredients) {
					if (ingredient.present && seen.add(ingredient.id)) {
						usages.getOrPut(ingredient.id, ::ArrayList).add(recipe)
					}
				}
			}
		}

		private fun parse(file: Path): RepoItem? {
			val name = file.fileName.toString()
			return try {
				val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
				val id = (json.text("internalname") ?: name.removeSuffix(JSON)).uppercase(Locale.ROOT)
				RepoItem(
					id = id,
					itemId = json.text("itemid").orEmpty(),
					displayName = json.text("displayname").orEmpty(),
					damage = json.int("damage"),
					lore = json.array("lore")?.map { it.textOrNull().orEmpty() } ?: emptyList(),
					nbttag = json.text("nbttag").orEmpty(),
					info = json.array("info").texts(),
					recipes = recipes(json, id),
					infoType = json.text("infoType").orEmpty(),
					craftText = json.text("crafttext").orEmpty(),
					slayerRequirement = json.text("slayer_req").orEmpty(),
					island = json.text("island").orEmpty(),
					x = json.int("x"),
					y = json.int("y"),
					z = json.int("z")
				)
			} catch (throwable: Throwable) {
				log.warn("Dhen could not read the repo item {}", name, throwable)
				null
			}
		}

		private fun normalized(displayName: String): String =
			withoutCodes(displayName).lowercase(Locale.ROOT).trim()

		private fun searchName(displayName: String): String =
			withoutCodes(displayName).removePrefix(PET_LEVEL_PREFIX).trim()
	}
}
