package io.github.dzkchen.dhen.data

import com.google.gson.JsonObject
import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ConstantsFixture
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoSource
import io.github.dzkchen.dhen.data.repo.RepoSync
import io.github.dzkchen.dhen.data.repo.RepoTransport
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.json as parseJson
import io.github.dzkchen.dhen.util.WebResponse
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path

internal object DataFixture {
	const val NO_BAZAAR = """{"success":true,"lastUpdated":1,"products":{}}"""

	const val NO_NPC = """{"items":[]}"""

	const val NO_SPARE = "{}"

	const val ANY_ITEM = """{"internalname":"ASPECT_OF_THE_END","displayname":"§5Aspect of the End"}"""

	val NEU = RepoSource("NotEnoughUpdates", "NotEnoughUpdates-REPO", "master")

	val OFFLINE = object : RepoTransport {
		override fun response(url: String): WebResponse = WebResponse(null)

		override fun download(url: String, destination: Path): Boolean = false
	}

	fun json(body: String): JsonObject = parseJson(body)

	fun installRepo(
		scope: CoroutineScope,
		root: Path,
		items: Map<String, String> = emptyMap(),
		constants: Map<String, String> = emptyMap()
	) {
		Files.createDirectories(root.resolve("items"))
		for ((id, body) in items) Files.writeString(root.resolve("items/$id.json"), body)
		if (constants.isNotEmpty()) Files.createDirectories(root.resolve("constants"))
		for ((name, body) in constants) Files.writeString(root.resolve("constants/$name.json"), body)
		Files.writeString(root.resolveSibling("${root.fileName}.commit"), "fixture")
		ItemRepo.install(scope, root, RepoSync(NEU, root, OFFLINE))
		ItemRepo.require()
	}

	fun installLeveling(scope: CoroutineScope, home: Path) =
		installRepo(scope, home.resolve("repo"), constants = mapOf("leveling" to ConstantsFixture.LEVELING))

	fun installPrices(
		scope: CoroutineScope,
		lowestBins: String,
		bazaar: String = NO_BAZAAR,
		npc: String = NO_NPC,
		spare: String = NO_SPARE
	) {
		val web = WebSource { url ->
			when {
				url.contains("bazaar") -> bazaar
				url.contains("tricked") -> lowestBins
				url.contains("eliteskyblock") -> spare
				else -> npc
			}
		}
		Prices.install(scope, EventBus(), Dispatchers.Unconfined, web, { 0L }) { true }
		Prices.require()
	}

	fun uninstall() {
		ItemRepo.uninstall()
		Prices.uninstall()
	}
}
