package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.textOrNull
import java.util.Locale

enum class RecipeKind {
	CRAFTING,
	FORGE,
	NPC_SHOP,
	KAT_UPGRADE
}

class ItemIngredient internal constructor(val id: String, val count: Int) {
	val present: Boolean get() = id.isNotEmpty()

	internal companion object {
		val NONE = ItemIngredient("", 0)
	}
}

class ItemRecipe internal constructor(
	val kind: RecipeKind,
	val owner: String,
	val ingredients: List<ItemIngredient>,
	val output: ItemIngredient,
	val seconds: Int
)

internal const val SKYBLOCK_COIN = "SKYBLOCK_COIN"

private const val TYPE = "type"
private const val IMPLIED_AMOUNT = 1

private val SLOTS = arrayOf("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")

internal fun ingredients(array: JsonArray?): Map<String, Int> {
	if (array == null || array.isEmpty) return emptyMap()
	val amounts = LinkedHashMap<String, Int>(array.size())
	for (element in array) {
		val parsed = ingredient(element.textOrNull()) ?: continue
		amounts.merge(parsed.id, parsed.count, Int::plus)
	}
	return amounts
}

internal fun recipes(json: JsonObject, owner: String): List<ItemRecipe> {
	val listed = json.getAsJsonArray("recipes")?.mapNotNull { recipe(it, owner) }.orEmpty()
	val single = recipe(json.get("recipe"), owner) ?: return listed
	return listOf(single) + listed
}

private fun recipe(element: JsonElement?, owner: String): ItemRecipe? {
	val json = element as? JsonObject ?: return null
	return when (json.text(TYPE)?.lowercase(Locale.ROOT)) {
		null, "crafting" -> crafting(json, owner)
		"forge" -> forge(json, owner)
		"npc_shop" -> npcShop(json, owner)
		"katgrade" -> katUpgrade(json, owner)
		else -> null
	}
}

private fun crafting(json: JsonObject, owner: String): ItemRecipe? {
	val slots = SLOTS.map { ingredient(json.text(it)) ?: ItemIngredient.NONE }
	if (slots.none(ItemIngredient::present)) return null
	return ItemRecipe(RecipeKind.CRAFTING, owner, slots, made(json, owner), 0)
}

private fun forge(json: JsonObject, owner: String): ItemRecipe? {
	val inputs = json.getAsJsonArray("inputs")?.mapNotNull { ingredient(it.textOrNull()) }.orEmpty()
	if (inputs.isEmpty()) return null
	return ItemRecipe(RecipeKind.FORGE, owner, inputs, made(json, owner), json.int("duration"))
}

private fun npcShop(json: JsonObject, owner: String): ItemRecipe? {
	val costs = json.getAsJsonArray("cost")?.mapNotNull(::cost).orEmpty()
	val result = ingredient(json.text("result")) ?: return null
	if (costs.isEmpty()) return null
	return ItemRecipe(RecipeKind.NPC_SHOP, owner, costs, result, 0)
}

private fun katUpgrade(json: JsonObject, owner: String): ItemRecipe? {
	val pet = ingredient(json.text("input")) ?: return null
	val upgraded = ingredient(json.text("output")) ?: return null
	val extras = json.getAsJsonArray("items")?.mapNotNull { ingredient(it.textOrNull()) }.orEmpty()
	val coins = json.int("coins")
	val inputs = buildList {
		add(pet)
		addAll(extras)
		if (coins > 0) add(ItemIngredient(SKYBLOCK_COIN, coins))
	}
	return ItemRecipe(RecipeKind.KAT_UPGRADE, owner, inputs, upgraded, json.int("time"))
}

private fun made(json: JsonObject, owner: String): ItemIngredient {
	val id = json.text("overrideOutputId")?.let { ingredient(it)?.id } ?: owner
	return ItemIngredient(id, json.int("count", IMPLIED_AMOUNT).coerceAtLeast(IMPLIED_AMOUNT))
}

private fun cost(element: JsonElement): ItemIngredient? {
	val json = element as? JsonObject ?: return ingredient(element.textOrNull())
	val listed = ingredient(json.text("item")) ?: return null
	val amount = json.int("cost", IMPLIED_AMOUNT).coerceAtLeast(IMPLIED_AMOUNT)
	return ItemIngredient(listed.id, amount * listed.count)
}

private fun ingredient(raw: String?): ItemIngredient? {
	if (raw.isNullOrBlank()) return null
	val separator = raw.lastIndexOf(':')
	if (separator <= 0) return ItemIngredient(raw.uppercase(Locale.ROOT), IMPLIED_AMOUNT)
	val amount = raw.substring(separator + 1).toDoubleOrNull()?.toInt() ?: IMPLIED_AMOUNT
	return ItemIngredient(raw.substring(0, separator).uppercase(Locale.ROOT), amount.coerceAtLeast(IMPLIED_AMOUNT))
}
