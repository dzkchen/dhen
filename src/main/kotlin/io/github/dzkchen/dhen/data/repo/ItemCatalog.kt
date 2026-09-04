package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
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
	val recipes: List<ItemRecipe>
)

class ItemCatalog private constructor(
	private val byId: Map<String, RepoItem>,
	private val byDisplayName: Map<String, String>,
	private val stacks: Map<String, ItemStack>,
	private val made: Map<String, List<ItemRecipe>>,
	private val usedIn: Map<String, List<ItemRecipe>>
) {
	private val pseudo = ConcurrentHashMap<String, ItemStack>()

	val size: Int get() = byId.size

	val ids: Set<String> get() = byId.keys

	fun item(id: String): RepoItem? = byId[id.uppercase(Locale.ROOT)]

	fun idFor(displayName: String): String? = byDisplayName[normalized(displayName)]

	fun stack(id: String): ItemStack? = stacks[id.uppercase(Locale.ROOT)]

	fun recipesFor(id: String): List<ItemRecipe> = made[id.uppercase(Locale.ROOT)].orEmpty()

	fun usages(id: String): List<ItemRecipe> = usedIn[id.uppercase(Locale.ROOT)].orEmpty()

	fun ingredientStack(id: String): ItemStack {
		val key = id.uppercase(Locale.ROOT)
		stacks[key]?.let { return it }
		return pseudo.computeIfAbsent(key) { if (it == SKYBLOCK_COIN) coinStack() else namedPlaceholder(it) }
	}

	fun recipeCount(kind: RecipeKind): Int = byId.values.sumOf { item -> item.recipes.count { it.kind == kind } }

	val searchNames: List<SearchName> by lazy {
		byId.values.mapNotNull { item ->
			val label = withoutCodes(item.displayName).removePrefix(PET_LEVEL_PREFIX).trim()
			if (label.isEmpty()) null else SearchName(label, label.lowercase(Locale.ROOT), item.id)
		}
	}

	internal companion object {
		private const val JSON = ".json"
		private const val PET_LEVEL_PREFIX = "[Lvl {LVL}] "
		private const val ITEMS = "items"
		private const val CONSTANTS = "constants"
		private const val MAX_FAILURE_RATE = 0.05

		val EMPTY = ItemCatalog(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		private val VARIANT = Regex(".\\d+$")

		fun read(root: Path, cache: Path? = null, commit: String = ""): ItemCatalog {
			val parsed = cache?.let { CatalogCache.read(it, commit) } ?: compile(root, cache, commit)
			return if (parsed.isEmpty()) EMPTY else assemble(parsed, root)
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

		private fun assemble(parsed: List<RepoItem>, root: Path): ItemCatalog {
			val pets = PetNumbers.read(root.resolve(CONSTANTS))
			val overlays = StackOverlays.read(root)
			val byId = LinkedHashMap<String, RepoItem>(parsed.size)
			val byDisplayName = HashMap<String, String>(parsed.size)
			val stacks = HashMap<String, ItemStack>(parsed.size)
			val made = HashMap<String, MutableList<ItemRecipe>>()
			val usages = HashMap<String, MutableList<ItemRecipe>>()
			for (item in parsed) {
				if (byId.put(item.id, item) != null) continue
				byDisplayName.putIfAbsent(normalized(item.displayName), item.id)
				stacks[item.id] = repoStack(item, pets).also { overlays.apply(item.id, it) }
				index(item, made, usages)
			}
			return ItemCatalog(byId, byDisplayName, stacks, made, usages)
		}

		private fun index(
			item: RepoItem,
			made: MutableMap<String, MutableList<ItemRecipe>>,
			usages: MutableMap<String, MutableList<ItemRecipe>>
		) {
			for (recipe in item.recipes) {
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
					recipes = recipes(json, id)
				)
			} catch (throwable: Throwable) {
				log.warn("Dhen could not read the repo item {}", name, throwable)
				null
			}
		}

		private fun normalized(displayName: String): String =
			withoutCodes(displayName).lowercase(Locale.ROOT).trim()
	}
}
