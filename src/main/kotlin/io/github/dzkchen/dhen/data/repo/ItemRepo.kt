package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.util.NanoClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
	private val requirements = AtomicInteger()
	private val activated = AtomicBoolean()

	private val published = AtomicReference(Published(null, RepoState.IDLE))

	@Volatile
	private var host: Host? = null

	private val catalog: ItemCatalog get() = published.get().catalog

	val constants: RepoConstants get() = published.get().constants

	val state: RepoState get() = published.get().state

	val commit: String? get() = published.get().commit

	val size: Int get() = catalog.size

	val required: Int get() = requirements.get()

	val ready: Boolean get() = state == RepoState.READY

	fun item(id: String): RepoItem? = catalog.item(id)

	fun idFor(displayName: String): String? = catalog.idFor(displayName)

	fun require(): Handle {
		val owner = host ?: return Handle {}
		requirements.incrementAndGet()
		if (owner.clock.nanoTime() >= published.get().retryAt && activated.compareAndSet(false, true)) {
			owner.scope.launch { load(owner) }
		}
		val released = AtomicBoolean()
		return Handle {
			if (released.compareAndSet(false, true) && host === owner) requirements.decrementAndGet()
		}
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
		requirements.set(0)
		activated.set(false)
	}

	private fun load(owner: Host) {
		if (!publish(owner) { it.copy(state = RepoState.SYNCING) }) return
		val read = try {
			val result = owner.sync.sync()
			if (result != SyncResult.UPDATED && result != SyncResult.UP_TO_DATE) {
				log.warn("Dhen could not refresh the item repo ({}), reading whatever is already on disk", result)
			}
			val catalog = ItemCatalog.read(owner.root.resolve(ITEMS))
			Published(
				owner,
				if (catalog.size > 0) RepoState.READY else RepoState.UNAVAILABLE,
				catalog,
				RepoConstants.read(owner.root.resolve(CONSTANTS)),
				owner.sync.syncedCommit()
			)
		} catch (throwable: Throwable) {
			log.error("Dhen could not load the item repo", throwable)
			Published(owner, RepoState.UNAVAILABLE)
		}
		val loaded = if (read.state != RepoState.UNAVAILABLE) read else
			read.copy(retryAt = owner.clock.nanoTime() + RETRY_AFTER.inWholeNanoseconds)
		if (!publish(owner) { loaded }) return
		if (loaded.state == RepoState.UNAVAILABLE) activated.set(false)
		log.info("Dhen item repo {} with {} items", loaded.state, loaded.catalog.size)
	}

	private fun publish(owner: Host, next: (Published) -> Published): Boolean =
		published.updateAndGet { if (it.host === owner) next(it) else it }.host === owner

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
