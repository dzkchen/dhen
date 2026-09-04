package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object CatalogCache {
	private const val SCHEMA_VERSION = 1
	private const val STAGING = ".compiling"
	private const val MAX_ENTRIES = 1 shl 22

	private val MAGIC = "DHNC".fold(0) { packed, character -> (packed shl 8) or character.code }
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	fun read(cache: Path, commit: String): List<RepoItem>? {
		if (!Files.isRegularFile(cache)) return null
		return try {
			DataInputStream(BufferedInputStream(Files.newInputStream(cache))).use { input ->
				if (input.readInt() != MAGIC || input.readInt() != SCHEMA_VERSION) return null
				if (input.text() != commit) return null
				val count = input.size()
				val items = ArrayList<RepoItem>(count)
				repeat(count) { items.add(input.item()) }
				if (input.readInt() != count) return null
				items
			}
		} catch (throwable: Throwable) {
			log.warn("Dhen could not read the compiled item catalog, recompiling it", throwable)
			null
		}
	}

	fun write(cache: Path, commit: String, items: List<RepoItem>) {
		val staging = cache.resolveSibling("${cache.fileName}$STAGING")
		try {
			cache.parent?.let(Files::createDirectories)
			DataOutputStream(BufferedOutputStream(Files.newOutputStream(staging))).use { output ->
				output.writeInt(MAGIC)
				output.writeInt(SCHEMA_VERSION)
				output.text(commit)
				output.writeInt(items.size)
				for (item in items) output.item(item)
				output.writeInt(items.size)
			}
			Files.move(staging, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		} catch (throwable: Throwable) {
			log.warn("Dhen could not compile the item catalog, it will be parsed again next time", throwable)
			runCatching { Files.deleteIfExists(staging) }
		}
	}

	private fun DataInputStream.item(): RepoItem {
		val id = text()
		return RepoItem(
			id = id,
			itemId = text(),
			displayName = text(),
			damage = readInt(),
			lore = list { text() },
			nbttag = text(),
			info = list { text() },
			recipes = list { recipe() }
		)
	}

	private fun DataOutputStream.item(item: RepoItem) {
		text(item.id)
		text(item.itemId)
		text(item.displayName)
		writeInt(item.damage)
		list(item.lore) { text(it) }
		text(item.nbttag)
		list(item.info) { text(it) }
		list(item.recipes) { recipe(it) }
	}

	private fun DataInputStream.recipe(): ItemRecipe = ItemRecipe(
		kind = RecipeKind.entries[readInt()],
		owner = text(),
		ingredients = list { ingredient() },
		output = ingredient(),
		seconds = readInt()
	)

	private fun DataOutputStream.recipe(recipe: ItemRecipe) {
		writeInt(recipe.kind.ordinal)
		text(recipe.owner)
		list(recipe.ingredients) { ingredient(it) }
		ingredient(recipe.output)
		writeInt(recipe.seconds)
	}

	private fun DataInputStream.ingredient(): ItemIngredient = ItemIngredient(text(), readInt())

	private fun DataOutputStream.ingredient(ingredient: ItemIngredient) {
		text(ingredient.id)
		writeInt(ingredient.count)
	}

	private fun DataInputStream.text(): String {
		val bytes = ByteArray(size())
		readFully(bytes)
		return String(bytes, StandardCharsets.UTF_8)
	}

	private fun DataOutputStream.text(value: String) {
		val bytes = value.toByteArray(StandardCharsets.UTF_8)
		writeInt(bytes.size)
		write(bytes)
	}

	private fun DataInputStream.size(): Int = readInt().also { require(it in 0..MAX_ENTRIES) { "corrupt catalog length $it" } }

	private inline fun <T> DataInputStream.list(read: () -> T): List<T> {
		val count = size()
		val values = ArrayList<T>(count)
		repeat(count) { values.add(read()) }
		return values
	}

	private inline fun <T> DataOutputStream.list(values: List<T>, write: (T) -> Unit) {
		writeInt(values.size)
		for (value in values) write(value)
	}
}
