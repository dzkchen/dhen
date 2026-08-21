package io.github.dzkchen.dhen.data

import io.github.dzkchen.dhen.data.price.Prices
import io.github.dzkchen.dhen.data.repo.ItemRepo
import io.github.dzkchen.dhen.data.repo.RepoSource
import io.github.dzkchen.dhen.data.repo.RepoSync
import io.github.dzkchen.dhen.data.repo.RepoTransport
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.util.WebSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path

internal object DataFixture {
	const val NO_BAZAAR = """{"success":true,"lastUpdated":1,"products":{}}"""

	const val NO_NPC = """{"items":[]}"""

	const val NO_SPARE = "{}"

	val NEU = RepoSource("NotEnoughUpdates", "NotEnoughUpdates-REPO", "master")

	val OFFLINE = object : RepoTransport {
		override fun text(url: String): String? = null

		override fun download(url: String, destination: Path): Boolean = false
	}

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
		ItemRepo.install(scope, root, RepoSync(NEU, root, OFFLINE))
		ItemRepo.require()
	}

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
