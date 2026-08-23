package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.data.Requirement
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.util.NanoClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

enum class RepoState {
	IDLE,
	SYNCING,
	READY,
	UNAVAILABLE
}

object ItemRepo {
	private const val ITEMS = "items"
	private const val CONSTANTS = "constants"

	private val NEU = RepoSource("NotEnoughUpdates", "NotEnoughUpdates-REPO", "master")
	private val RETRY_AFTER = 5.minutes
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val requirement = Requirement()
	private val activated = AtomicBoolean()

	private val published = AtomicReference(Published(null, RepoState.IDLE))

	@Volatile
	private var host: Host? = null

	private val catalog: ItemCatalog get() = published.get().catalog

	val constants: RepoConstants get() = published.get().constants

	val state: RepoState get() = published.get().state

	val commit: String? get() = published.get().commit

	val size: Int get() = catalog.size

	val required: Int get() = requirement.count

	val ready: Boolean get() = state == RepoState.READY

	fun item(id: String): RepoItem? = catalog.item(id)

	fun idFor(displayName: String): String? = catalog.idFor(displayName)

	fun require(): Handle {
		val owner = host ?: return Handle {}
		return requirement.require(alive = { host === owner }, taken = { startLoad(owner) })
	}

	internal fun install(
		scope: CoroutineScope,
		root: Path,
		sync: RepoSync = RepoSync(NEU, root),
		clock: NanoClock = NanoClock.SYSTEM
	) {
		uninstall()
		val owner = Host(scope, root, sync, clock)
		published.set(Published(owner, RepoState.IDLE))
		host = owner
	}

	internal fun uninstall() {
		host = null
		published.set(Published(null, RepoState.IDLE))
		requirement.reset()
		activated.set(false)
	}

	private fun startLoad(owner: Host) {
		if (owner.clock.nanoTime() < published.get().retryAt || !activated.compareAndSet(false, true)) return
		owner.scope.launch { load(owner) }
	}

	private fun load(owner: Host) {
		if (publish(owner) { it.copy(state = RepoState.SYNCING) } == null) return
		val read = try {
			val result = owner.sync.sync()
			if (result != SyncResult.UPDATED && result != SyncResult.UP_TO_DATE) {
				log.warn("Dhen could not refresh the item repo ({}), reading whatever is already on disk", result)
			}
			Reading(
				ItemCatalog.read(owner.root.resolve(ITEMS)),
				RepoConstants.read(owner.root.resolve(CONSTANTS)),
				owner.sync.syncedCommit()
			)
		} catch (throwable: Throwable) {
			log.error("Dhen could not load the item repo", throwable)
			null
		}
		val retryAt = owner.clock.nanoTime() + RETRY_AFTER.inWholeNanoseconds
		val loaded = publish(owner) { held ->
			if (read == null) held.copy(state = RepoState.UNAVAILABLE, retryAt = retryAt) else {
				val next = held.copy(catalog = read.catalog, constants = read.constants, commit = read.commit)
				if (next.catalog.size > 0) next.copy(state = RepoState.READY, retryAt = 0L)
				else next.copy(state = RepoState.UNAVAILABLE, retryAt = retryAt)
			}
		} ?: return
		if (loaded.state == RepoState.UNAVAILABLE) activated.set(false)
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
		val commit: String? = null,
		val retryAt: Long = 0L
	)

	private class Host(val scope: CoroutineScope, val root: Path, val sync: RepoSync, val clock: NanoClock)
}
