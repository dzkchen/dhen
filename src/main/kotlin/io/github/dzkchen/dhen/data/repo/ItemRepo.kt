package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.RequirementPump
import io.github.dzkchen.dhen.data.price.PriceTables
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.util.NanoClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

enum class RepoState {
	IDLE,
	SYNCING,
	READY,
	UNAVAILABLE
}

object ItemRepo {
	private const val CONSTANTS = "constants"
	private const val CATALOG = ".catalog"
	private const val SHARDS = "shards"
	private const val RETRY_LIMIT = 6

	private val NEU = RepoSource("NotEnoughUpdates", "NotEnoughUpdates-REPO", "master")
	private val RETRY_AFTER = 5.minutes
	private val MAX_RETRY_AFTER = 1.hours
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val pump = RequirementPump()

	private val published = AtomicReference(Published(null, RepoState.IDLE))

	@Volatile
	private var host: Host? = null

	private val catalog: ItemCatalog get() = published.get().catalog

	val constants: RepoConstants get() = published.get().constants

	val state: RepoState get() = published.get().state

	val commit: String? get() = published.get().commit

	val size: Int get() = catalog.size

	val ids: Set<String> get() = catalog.ids

	val required: Int get() = pump.count

	internal val retrying: Boolean get() = pump.polling

	val ready: Boolean get() = state == RepoState.READY

	internal fun active(): Boolean = host != null

	fun item(id: String): RepoItem? = catalog.item(id) ?: PriceTables.neuId(id)?.let(catalog::item)

	fun idFor(displayName: String): String? = catalog.idFor(displayName)

	fun searchNames(): List<SearchName> = catalog.searchNames

	fun stack(id: String): ItemStack? = catalog.stack(id)

	fun ingredientStack(id: String): ItemStack = catalog.ingredientStack(id)

	fun recipesFor(id: String): List<ItemRecipe> {
		val held = published.get()
		val made = held.catalog.recipesFor(id)
		val fusions = held.fusions.fusionsFor(id.uppercase(Locale.ROOT))
		return when {
			fusions.isEmpty() -> made.ifEmpty { held.catalog.infoCard(id) }
			made.isEmpty() -> fusions
			else -> made + fusions
		}
	}

	fun usages(id: String): List<ItemRecipe> {
		val held = published.get()
		val used = held.catalog.usages(id)
		val fusions = held.fusions.fusionsWith(id.uppercase(Locale.ROOT))
		return if (fusions.isEmpty()) used else used + fusions
	}

	fun reforges(id: String): List<ItemRecipe> = catalog.reforges(id)

	fun recipeCount(kind: RecipeKind): Int =
		catalog.recipeCount(kind) + if (kind == RecipeKind.SHARD_FUSION) published.get().fusions.pairs else 0

	fun require(): Handle {
		val owner = host ?: return Handle {}
		return pump.requireOnTake({ host === owner }) { owner.scope.launch { drive(owner) } }
	}

	internal fun install(
		scope: CoroutineScope,
		root: Path,
		sync: RepoSync = RepoSync(NEU, root),
		clock: NanoClock = NanoClock.SYSTEM,
		retryAfter: Duration = RETRY_AFTER,
		shards: (Path, RepoConstants) -> ShardCatalogue = { _, _ -> ShardCatalogue.EMPTY }
	) {
		uninstall()
		val owner = Host(scope, sync, clock, retryAfter, root.resolveSibling(SHARDS), shards)
		sync.changeInstallation {
			published.set(Published(owner, RepoState.IDLE))
			host = owner
		}
	}

	internal fun uninstall() {
		val owner = host
		if (owner == null) {
			published.set(Published(null, RepoState.IDLE))
			pump.reset()
			return
		}
		owner.sync.changeInstallation {
			if (host !== owner) return@changeInstallation
			host = null
			published.set(Published(null, RepoState.IDLE))
			pump.reset()
		}
	}

	private suspend fun drive(owner: Host) = coroutineScope {
		var attempts = RETRY_LIMIT
		while (isActive) {
			val current = published.get()
			if (current.host !== owner || current.state == RepoState.READY) return@coroutineScope
			if (attempts == 0) {
				log.warn(
					"Dhen stopped retrying the item repo after {} attempts; /dhen debug repo download asks for it again",
					RETRY_LIMIT
				)
				return@coroutineScope
			}
			if (loadIfDue(owner) { host === owner && pump.count > 0 }) attempts-- else delay(owner.retryAfter)
		}
	}

	private fun loadIfDue(owner: Host, stillWanted: () -> Boolean): Boolean {
		if (owner.clock.nanoTime() < published.get().retryAt) return false
		val displaced = beginSyncing(owner) ?: return false
		load(owner, displaced, stillWanted)
		return true
	}

	private fun beginSyncing(owner: Host): RepoState? {
		while (true) {
			val held = published.get()
			if (held.host !== owner || held.state == RepoState.SYNCING || held.state == RepoState.READY) return null
			if (published.compareAndSet(held, held.copy(state = RepoState.SYNCING))) return held.state
		}
	}

	private fun load(owner: Host, displaced: RepoState, stillWanted: () -> Boolean) {
		var retryAfter: Duration? = null
		val read = try {
			val outcome = owner.sync.sync(stillWanted)
			val result = outcome.result
			retryAfter = outcome.retryAfter
			if (result == SyncResult.ABANDONED) {
				publish(owner) { it.copy(state = displaced) }
				return
			}
			if (result != SyncResult.UPDATED && result != SyncResult.UP_TO_DATE) {
				log.warn("Dhen could not refresh the item repo ({}), reading the last complete repo on disk", result)
			}
			owner.sync.readMarked { root, commit ->
				val constants = RepoConstants.read(root.resolve(CONSTANTS))
				Reading(
					ItemCatalog.read(root, root.resolveSibling("${root.fileName}$CATALOG"), commit, constants),
					constants,
					commit
				)
			}
		} catch (throwable: Throwable) {
			log.error("Dhen could not load the item repo", throwable)
			null
		}
		val fusions = try {
			read?.let { owner.shards(owner.shardCache, it.constants) } ?: ShardCatalogue.EMPTY
		} catch (throwable: Throwable) {
			log.error("Dhen could not load the shard fusion data", throwable)
			ShardCatalogue.EMPTY
		}
		val retryDelay = retryAfter?.coerceIn(owner.retryAfter, maxOf(owner.retryAfter, MAX_RETRY_AFTER))
			?: owner.retryAfter
		val retryAt = owner.clock.nanoTime() + retryDelay.inWholeNanoseconds
		val loaded = publish(owner) { held ->
			if (read == null) held.copy(state = RepoState.UNAVAILABLE, retryAt = retryAt) else {
				val next = held.copy(
					catalog = read.catalog,
					constants = read.constants,
					fusions = fusions,
					commit = read.commit
				)
				if (next.catalog.size > 0) next.copy(state = RepoState.READY, retryAt = 0L)
				else next.copy(state = RepoState.UNAVAILABLE, retryAt = retryAt)
			}
		} ?: return
		log.info("Dhen item repo {} with {} items", loaded.state, loaded.catalog.size)
	}

	private fun publish(owner: Host, next: (Published) -> Published): Published? =
		published.updateAndGet { if (it.host === owner) next(it) else it }.takeIf { it.host === owner }

	private class Reading(val catalog: ItemCatalog, val constants: RepoConstants, val commit: String?)

	private data class Published(
		val host: Host?,
		val state: RepoState,
		val catalog: ItemCatalog = ItemCatalog.EMPTY,
		val constants: RepoConstants = RepoConstants.EMPTY,
		val fusions: ShardCatalogue = ShardCatalogue.EMPTY,
		val commit: String? = null,
		val retryAt: Long = 0L
	)

	private class Host(
		val scope: CoroutineScope,
		val sync: RepoSync,
		val clock: NanoClock,
		val retryAfter: Duration,
		val shardCache: Path,
		val shards: (Path, RepoConstants) -> ShardCatalogue
	)
}
