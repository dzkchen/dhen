package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.util.ClientThreadDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class CustomSoundActionsTest {
	@TempDir
	lateinit var directory: Path

	private val client = ClientThreadDispatcher()
	private val scope = CoroutineScope(Dispatchers.Unconfined)

	@AfterEach
	fun release() = CustomSoundPack.uninstall()

	@Test
	fun `open creates the folder before revealing it on the client thread`() {
		CustomSoundPack.install(directory, scope)
		var revealed: Path? = null
		val actions = actions(reveal = { revealed = it })

		actions.openFolder()

		assertEquals(null, revealed)
		client.drainQueue()
		assertTrue(Files.isDirectory(revealed))
	}

	@Test
	fun `uninstall prevents a prepared folder from being revealed`() {
		CustomSoundPack.install(directory, scope)
		var revealed = false
		val actions = actions(reveal = { revealed = true })

		actions.openFolder()
		CustomSoundPack.uninstall()
		client.drainQueue()

		assertFalse(revealed)
	}

	@Test
	fun `only the newest repeated scan starts a resource reload`() {
		CustomSoundPack.install(directory, scope)
		var reloads = 0
		val actions = actions(reload = {
			reloads++
			CompletableFuture.completedFuture(null)
		})

		actions.reload()
		actions.reload()
		client.drainQueue()

		assertEquals(1, reloads)
	}

	@Test
	fun `a populated initial scan starts one silent resource reload`() {
		CustomSoundPack.install(directory, scope)
		val sounds = directory.resolve(CustomSoundPack.DIRECTORY)
		Files.write(sounds.resolve("horn.ogg"), byteArrayOf(1))
		val result = CustomSoundPack.refresh {}
		var reloads = 0
		val notices = mutableListOf<String>()
		val actions = CustomSoundActions(scope, client, notices::add, {}, {
			reloads++
			CompletableFuture.completedFuture(null)
		})

		scope.launch { actions.afterInitialScan(result) }
		client.drainQueue()
		client.drainQueue()

		assertEquals(1, reloads)
		assertTrue(notices.isEmpty())
	}

	@Test
	fun `a folder scan failure is reported after stale sounds are reloaded away`() {
		Files.write(directory.resolve(CustomSoundPack.DIRECTORY), byteArrayOf(1))
		CustomSoundPack.install(directory, scope)
		val notices = mutableListOf<String>()
		val actions = CustomSoundActions(scope, client, notices::add, {}, { CompletableFuture.completedFuture(null) })

		actions.reload()
		client.drainQueue()
		client.drainQueue()

		assertTrue(notices.single().startsWith("Could not read custom sounds:"))
	}

	@Test
	fun `uninstall invalidates a scan before its queued resource reload`() {
		CustomSoundPack.install(directory, scope)
		var reloaded = false
		val actions = actions(reload = {
			reloaded = true
			CompletableFuture.completedFuture(null)
		})

		actions.reload()
		CustomSoundPack.uninstall()
		client.drainQueue()

		assertFalse(reloaded)
	}

	private fun actions(
		reveal: (Path) -> Unit = {},
		reload: () -> CompletableFuture<Void> = { CompletableFuture.completedFuture(null) }
	): CustomSoundActions = CustomSoundActions(scope, client, {}, reveal, reload)
}
