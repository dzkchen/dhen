package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.flag
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.obj
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.texts
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

internal object GardenMutations {
	private const val ASSET = "assets/dhen/skyblock/mutations.json"
	private const val DEFAULT_SURFACE = "Farmland"
	private const val DEFAULT_RARITY = "COMMON"

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	val recipes: List<ItemRecipe> by lazy(::read)

	private fun read(): List<ItemRecipe> {
		val stream = GardenMutations::class.java.classLoader.getResourceAsStream(ASSET)
		if (stream == null) {
			log.warn("Dhen could not find the bundled garden mutation layouts at {}", ASSET)
			return emptyList()
		}
		return try {
			val root = stream.use { InputStreamReader(it, StandardCharsets.UTF_8).use(JsonParser::parseReader) }
			val mutations = (root as? JsonObject)?.obj("mutations") ?: return emptyList()
			mutations.keySet().mapNotNull { id -> mutations.obj(id)?.let { mutation(id, it) } }
		} catch (throwable: Throwable) {
			log.warn("Dhen could not read the bundled garden mutation layouts", throwable)
			emptyList()
		}
	}

	private fun mutation(id: String, json: JsonObject): ItemRecipe {
		val size = json.int("gridSize", 1)
		val rows = json.array("layout")?.map { row -> (row as? JsonArray).texts() }.orEmpty()
		val planted = LinkedHashMap<String, Int>()
		for (row in rows) {
			for (cell in row) {
				if (!cell.startsWith(MutationPlot.PLANTED)) continue
				planted.merge(cell.substring(MutationPlot.PLANTED.length), 1, Int::plus)
			}
		}
		val coins = json.int("costCoins")
		val sown = buildList {
			planted.mapTo(this) { ItemIngredient(it.key, it.value) }
			if (coins > 0) add(ItemIngredient(SKYBLOCK_COIN, coins))
		}
		return ItemRecipe(RecipeKind.GARDEN_MUTATION, id, sown, ItemIngredient(id, 1), 0, plot(json, size, rows))
	}

	private fun plot(json: JsonObject, size: Int, rows: List<List<String>>): MutationPlot = MutationPlot(
		name = json.text("name").orEmpty(),
		rarity = json.text("rarity") ?: DEFAULT_RARITY,
		size = size,
		surface = json.text("surface") ?: DEFAULT_SURFACE,
		watered = json.flag("needsWater"),
		stages = json.int("stages"),
		copper = json.int("rewardCopper"),
		rows = rows,
		spreading = json.array("spreadingConditions")?.mapNotNull { element ->
			val entry = element as? JsonObject ?: return@mapNotNull null
			SpreadingCondition(
				entry.text("itemId").orEmpty(),
				entry.int("count"),
				entry.text("text").orEmpty()
			)
		}.orEmpty(),
		effects = json.array("effects")?.mapNotNull { element ->
			val entry = element as? JsonObject ?: return@mapNotNull null
			MutationEffect(entry.text("name").orEmpty(), entry.text("description").orEmpty())
		}.orEmpty(),
		mechanic = json.text("specialMechanic").orEmpty()
	)
}
