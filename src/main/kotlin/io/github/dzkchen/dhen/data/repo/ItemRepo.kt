package io.github.dzkchen.dhen.data.repo

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.Handle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val requirements = AtomicInteger()
	private val activated = AtomicBoolean()
	private val installation = AtomicInteger()

	@Volatile
	private var host: Host? = null

	@Volatile
	private var catalog: ItemCatalog = ItemCatalog.EMPTY

	@Volatile
	var constants: RepoConstants = RepoConstants.EMPTY
		private set

	@Volatile
	var state: RepoState = RepoState.IDLE
		private set

	@Volatile
	var commit: String? = null
		private set

	val size: Int get() = catalog.size

	val required: Int get() = requirements.get()

	fun item(id: String): RepoItem? = catalog.item(id)

	fun idFor(displayName: String): String? = catalog.idFor(displayName)

	fun require(): Handle {
		val host = host ?: return Handle {}
		val installed = installation.get()
		requirements.incrementAndGet()
		if (activated.compareAndSet(false, true)) host.scope.launch { load(host) }
		val released = AtomicBoolean()
		return Handle {
			if (released.compareAndSet(false, true) && installation.get() == installed) requirements.decrementAndGet()
		}
	}

	internal fun install(scope: CoroutineScope, root: Path, sync: RepoSync = RepoSync(NEU, root)) {
		uninstall()
		host = Host(scope, root, sync)
	}

	internal fun uninstall() {
		host = null
		installation.incrementAndGet()
		requirements.set(0)
		activated.set(false)
		catalog = ItemCatalog.EMPTY
		constants = RepoConstants.EMPTY
		state = RepoState.IDLE
		commit = null
	}

	private fun load(host: Host) {
		state = RepoState.SYNCING
		state = try {
			val result = host.sync.sync()
			if (result != SyncResult.UPDATED && result != SyncResult.UP_TO_DATE) {
				log.warn("Dhen could not refresh the item repo ({}), reading whatever is already on disk", result)
			}
			catalog = ItemCatalog.read(host.root.resolve(ITEMS))
			constants = RepoConstants.read(host.root.resolve(CONSTANTS))
			commit = host.sync.syncedCommit()
			if (catalog.size > 0) RepoState.READY else RepoState.UNAVAILABLE
		} catch (throwable: Throwable) {
			log.error("Dhen could not load the item repo", throwable)
			RepoState.UNAVAILABLE
		}
		log.info("Dhen item repo {} with {} items", state, catalog.size)
	}

	private class Host(val scope: CoroutineScope, val root: Path, val sync: RepoSync)
}
