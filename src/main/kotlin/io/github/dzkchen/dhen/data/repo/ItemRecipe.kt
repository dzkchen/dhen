package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.dzkchen.dhen.util.number
import io.github.dzkchen.dhen.util.text
import java.util.Locale

class ItemRecipe internal constructor(val ingredients: Map<String, Int>, val output: Int)

private const val CRAFTING = "crafting"
private const val IMPLIED_AMOUNT = 1

private val SLOTS = arrayOf("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")

internal fun ingredients(array: JsonArray?): Map<String, Int> {
	if (array == null || array.isEmpty) return emptyMap()
	val amounts = LinkedHashMap<String, Int>(array.size())
	for (element in array) amounts.add(element)
	return amounts
}

internal fun recipes(json: JsonObject): List<ItemRecipe> {
	val listed = json.getAsJsonArray("recipes")?.mapNotNull(::crafting).orEmpty()
	val single = crafting(json.get("recipe")) ?: return listed
	return listOf(single) + listed
}

private fun crafting(element: JsonElement?): ItemRecipe? {
	val json = element as? JsonObject ?: return null
	val type = json.text("type")
	if (type != null && type != CRAFTING) return null
	val ingredients = LinkedHashMap<String, Int>(SLOTS.size)
	for (slot in SLOTS) ingredients.add(json.get(slot))
	if (ingredients.isEmpty()) return null
	val output = json.number("count")?.toInt() ?: IMPLIED_AMOUNT
	return ItemRecipe(ingredients, output.coerceAtLeast(IMPLIED_AMOUNT))
}

private fun MutableMap<String, Int>.add(element: JsonElement?) {
	val raw = element?.takeIf(JsonElement::isJsonPrimitive)?.asString
	if (raw.isNullOrEmpty()) return
	val amount = raw.substringAfter(':', "").toIntOrNull() ?: IMPLIED_AMOUNT
	merge(raw.substringBefore(':').uppercase(Locale.ROOT), amount, Int::plus)
}
