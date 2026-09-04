package io.github.dzkchen.dhen.data.repo

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.util.WebClient
import io.github.dzkchen.dhen.util.text
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.StringReader
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Locale

private const val SHARD_BITS = 12
private const val SHARD_MASK = (1 shl SHARD_BITS) - 1
private const val QUANTITY_BITS = 4
private const val QUANTITY_MASK = (1 shl QUANTITY_BITS) - 1

internal class ShardCatalogue private constructor(
	private val ids: List<String>,
	private val amounts: IntArray,
	private val index: Map<String, Int>,
	private val pairsByOutput: Array<IntArray?>,
	private val outputsByInput: Array<IntArray?>,
	val pairs: Int
) {
	val shards: Int get() = ids.size

	fun fusionsFor(id: String): List<ItemRecipe> {
		val output = index[id] ?: return emptyList()
		val packed = pairsByOutput[output] ?: return emptyList()
		return packed.map { recipe(output, it) }
	}

	fun fusionsWith(id: String): List<ItemRecipe> {
		val input = index[id] ?: return emptyList()
		val outputs = outputsByInput[input] ?: return emptyList()
		return outputs.flatMap { output ->
			val packed = pairsByOutput[output] ?: return@flatMap emptyList()
			packed.filter { first(it) == input || second(it) == input }.map { recipe(output, it) }
		}
	}

	private fun recipe(output: Int, packed: Int): ItemRecipe {
		val first = first(packed)
		val second = second(packed)
		val spent = listOf(
			ItemIngredient(ids[first], amounts[first]),
			ItemIngredient(ids[second], amounts[second])
		)
		val made = ItemIngredient(ids[output], packed and QUANTITY_MASK)
		return ItemRecipe(RecipeKind.SHARD_FUSION, ids[output], spent, made, 0)
	}

	private fun first(packed: Int): Int = (packed ushr (SHARD_BITS + QUANTITY_BITS)) and SHARD_MASK

	private fun second(packed: Int): Int = (packed ushr QUANTITY_BITS) and SHARD_MASK

	internal companion object {
		val EMPTY = ShardCatalogue(emptyList(), IntArray(0), emptyMap(), emptyArray(), emptyArray(), 0)

		fun of(shards: ShardTable, collected: Array<MutableList<Int>?>): ShardCatalogue {
			val count = shards.ids.size
			val pairsByOutput = arrayOfNulls<IntArray>(count)
			val outputs = arrayOfNulls<MutableSet<Int>>(count)
			var pairs = 0
			for (output in 0 until count) {
				val listed = collected[output] ?: continue
				pairsByOutput[output] = listed.toIntArray()
				pairs += listed.size
				for (packed in listed) {
					val first = (packed ushr (SHARD_BITS + QUANTITY_BITS)) and SHARD_MASK
					val second = (packed ushr QUANTITY_BITS) and SHARD_MASK
					outputs[first] = (outputs[first] ?: LinkedHashSet()).apply { add(output) }
					outputs[second] = (outputs[second] ?: LinkedHashSet()).apply { add(output) }
				}
			}
			if (pairs == 0) return EMPTY
			return ShardCatalogue(
				shards.ids,
				shards.amounts,
				shards.ids.withIndex().associate { (at, id) -> id to at },
				pairsByOutput,
				Array(count) { outputs[it]?.toIntArray() },
				pairs
			)
		}
	}
}

internal class ShardTable(val ids: List<String>, val amounts: IntArray, val byGameId: Map<String, Int>)

internal object ShardFusions {
	private const val CACHE = "fusion-data.json"
	private const val MARKER = "fusion-data.commit"
	private const val STAGING = ".downloading"
	private const val DATA_PATH = "public/fusion-data.json"
	private const val MAX_SHARDS = SHARD_MASK
	private const val DEFAULT_FUSE_AMOUNT = 2

	private val GITHUB = mapOf("Accept" to "application/vnd.github+json")
	private val SOURCE = RepoSource("Campionnn", "SkyShards", "master")
	private val DOWNLOAD_DEADLINE: Duration = Duration.ofMinutes(2)
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	fun read(directory: Path, constants: RepoConstants, web: WebClient = WebClient(GITHUB)): ShardCatalogue {
		val cache = directory.resolve(CACHE)
		refresh(directory, cache, web)
		if (!Files.isRegularFile(cache)) return ShardCatalogue.EMPTY
		return try {
			catalogue(Files.readString(cache), constants)
		} catch (throwable: Throwable) {
			log.warn("Dhen could not read the shard fusion data, discarding it", throwable)
			discard(directory, cache)
			ShardCatalogue.EMPTY
		}
	}

	private fun discard(directory: Path, cache: Path) {
		runCatching {
			Files.deleteIfExists(cache)
			Files.deleteIfExists(directory.resolve(MARKER))
		}
	}

	fun catalogue(json: String, constants: RepoConstants): ShardCatalogue {
		val shards = table(json, constants)
		if (shards.ids.isEmpty()) return ShardCatalogue.EMPTY
		val collected = arrayOfNulls<MutableList<Int>>(shards.ids.size)
		reader(json).use { reader ->
			reader.beginObject()
			while (reader.hasNext()) {
				if (reader.nextName() != "recipes") reader.skipValue() else fusions(reader, shards, collected)
			}
			reader.endObject()
		}
		return ShardCatalogue.of(shards, collected)
	}

	private fun refresh(directory: Path, cache: Path, web: WebClient) {
		val marker = directory.resolve(MARKER)
		val latest = latestCommit(web) ?: return
		if (Files.isRegularFile(cache) && Files.isRegularFile(marker) && Files.readString(marker).trim() == latest) {
			return
		}
		val staging = cache.resolveSibling("${cache.fileName}$STAGING")
		try {
			Files.createDirectories(directory)
			val downloaded = web.send(
				SOURCE.rawUrl(latest, DATA_PATH),
				HttpResponse.BodyHandlers.ofFile(staging),
				DOWNLOAD_DEADLINE
			)
			if (downloaded == null || Files.size(staging) == 0L) return
			Files.move(staging, cache, StandardCopyOption.REPLACE_EXISTING)
			Files.writeString(marker, latest)
		} catch (throwable: Throwable) {
			log.warn("Dhen could not download the shard fusion data", throwable)
		} finally {
			runCatching { Files.deleteIfExists(staging) }
		}
	}

	private fun latestCommit(web: WebClient): String? {
		val body = web.text(SOURCE.commitUrl) ?: return null
		return try {
			JsonParser.parseString(body).asJsonObject.text("sha")
		} catch (throwable: Throwable) {
			log.warn("Dhen could not read the latest {} commit", SOURCE.repo, throwable)
			null
		}
	}

	private fun fusions(reader: JsonReader, shards: ShardTable, collected: Array<MutableList<Int>?>) {
		reader.beginObject()
		while (reader.hasNext()) {
			val output = shards.byGameId[reader.nextName()]
			if (output == null) {
				reader.skipValue()
				continue
			}
			reader.beginObject()
			while (reader.hasNext()) {
				val quantity = reader.nextName().toIntOrNull()?.coerceIn(1, QUANTITY_MASK)
				if (quantity == null) reader.skipValue() else pairs(reader, shards, output, quantity, collected)
			}
			reader.endObject()
		}
		reader.endObject()
	}

	private fun pairs(
		reader: JsonReader,
		shards: ShardTable,
		output: Int,
		quantity: Int,
		collected: Array<MutableList<Int>?>
	) {
		reader.beginArray()
		while (reader.hasNext()) {
			reader.beginArray()
			val first = shards.byGameId[reader.nextString()]
			val second = shards.byGameId[reader.nextString()]
			while (reader.hasNext()) reader.skipValue()
			reader.endArray()
			if (first == null || second == null) continue
			val packed = (first shl (SHARD_BITS + QUANTITY_BITS)) or (second shl QUANTITY_BITS) or quantity
			collected[output] = (collected[output] ?: ArrayList()).apply { add(packed) }
		}
		reader.endArray()
	}

	private fun table(json: String, constants: RepoConstants): ShardTable {
		val byGameId = HashMap<String, AttributeShard>(constants.attributeShards.size)
		val byBazaarName = HashMap<String, AttributeShard>(constants.attributeShards.size)
		for (shard in constants.attributeShards) {
			if (shard.shardId.isNotEmpty()) byGameId.putIfAbsent(shard.shardId, shard)
			if (shard.bazaarName.isNotEmpty()) byBazaarName.putIfAbsent(shard.bazaarName, shard)
		}
		if (byGameId.isEmpty() && byBazaarName.isEmpty()) {
			log.info("Dhen read no attribute shards from the item repo, so shard fusions cannot be resolved")
			return ShardTable(emptyList(), IntArray(0), emptyMap())
		}
		val ids = ArrayList<String>()
		val amounts = ArrayList<Int>()
		val indices = HashMap<String, Int>()
		reader(json).use { reader ->
			reader.beginObject()
			while (reader.hasNext()) {
				if (reader.nextName() != "shards") {
					reader.skipValue()
					continue
				}
				reader.beginObject()
				while (reader.hasNext()) {
					val gameId = reader.nextName()
					val listed = listed(reader)
					val known = byGameId[gameId] ?: byBazaarName[listed.bazaarName] ?: continue
					if (ids.size > MAX_SHARDS || indices.containsKey(gameId) || known.id in ids) continue
					indices[gameId] = ids.size
					ids += known.id
					amounts += listed.amount
				}
				reader.endObject()
			}
			reader.endObject()
		}
		return ShardTable(ids, amounts.toIntArray(), indices)
	}

	private fun listed(reader: JsonReader): Listed {
		var bazaarName = ""
		var amount = DEFAULT_FUSE_AMOUNT
		reader.beginObject()
		while (reader.hasNext()) {
			when (reader.nextName()) {
				"internal_id" -> bazaarName = reader.nextString().uppercase(Locale.ROOT)
				"fuse_amount" -> amount = reader.nextInt().coerceAtLeast(1)
				else -> reader.skipValue()
			}
		}
		reader.endObject()
		return Listed(bazaarName, amount)
	}

	private fun reader(json: String): JsonReader = JsonReader(BufferedReader(StringReader(json)))

	private class Listed(val bazaarName: String, val amount: Int)
}
