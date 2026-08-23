package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonParser
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.withoutCodes
import io.github.dzkchen.dhen.util.array
import io.github.dzkchen.dhen.util.int
import io.github.dzkchen.dhen.util.text
import io.github.dzkchen.dhen.util.textOrNull
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.streams.asSequence

data class RepoItem(
	val id: String,
	val itemId: String,
	val displayName: String,
	val damage: Int,
	val lore: List<String>,
	val recipes: List<ItemRecipe>
)

class ItemCatalog private constructor(
	private val byId: Map<String, RepoItem>,
	private val byDisplayName: Map<String, String>
) {
	val size: Int get() = byId.size

	val ids: Set<String> get() = byId.keys

	fun item(id: String): RepoItem? = byId[id.uppercase(Locale.ROOT)]

	fun idFor(displayName: String): String? = byDisplayName[normalized(displayName)]

	internal companion object {
		private const val JSON = ".json"

		val EMPTY = ItemCatalog(emptyMap(), emptyMap())

		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

		fun read(items: Path): ItemCatalog {
			if (!Files.isDirectory(items)) return EMPTY
			val byId = LinkedHashMap<String, RepoItem>()
			val byDisplayName = HashMap<String, String>()
			Files.list(items).use { listing ->
				for (file in listing.asSequence().sorted()) {
					val item = parse(file) ?: continue
					if (byId.put(item.id, item) != null) continue
					byDisplayName.putIfAbsent(normalized(item.displayName), item.id)
				}
			}
			return ItemCatalog(byId, byDisplayName)
		}

		private fun parse(file: Path): RepoItem? {
			val name = file.fileName.toString()
			if (!name.endsWith(JSON)) return null
			return try {
				val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
				val id = json.text("internalname") ?: name.removeSuffix(JSON)
				RepoItem(
					id = id.uppercase(Locale.ROOT),
					itemId = json.text("itemid").orEmpty(),
					displayName = json.text("displayname").orEmpty(),
					damage = json.int("damage"),
					lore = json.array("lore")?.map { it.textOrNull().orEmpty() } ?: emptyList(),
					recipes = recipes(json)
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
