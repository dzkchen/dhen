package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.util.reason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

internal class CustomSoundActions(
	private val scope: CoroutineScope,
	private val client: CoroutineContext,
	private val notify: (String) -> Unit,
	private val reveal: (Path) -> Unit,
	private val reloadResources: () -> CompletableFuture<Void>
) {
	init {
		ClientPrefs.openSoundsFolder.value = ::openFolder
		ClientPrefs.reloadCustomSounds.value = ::reload
	}

	internal fun openFolder() {
		scope.launch {
			val folder = try {
				CustomSoundPack.prepareFolder()
			} catch (exception: Exception) {
				log.warn("Could not create the custom sounds folder", exception)
				withContext(client) { notify("Could not open the sounds folder: ${exception.reason()}.") }
				return@launch
			} ?: return@launch
			withContext(client) {
				if (!CustomSoundPack.ownsFolder(folder)) return@withContext
				try {
					reveal(folder)
				} catch (exception: Exception) {
					log.warn("Could not show the custom sounds folder {}", folder, exception)
					notify("Could not open your file browser; the sounds folder is $folder.")
				}
			}
		}
	}

	internal fun reload() {
		scope.launch {
			val result = CustomSoundPack.refresh()
			withContext(client) { beginReload(result, report = true) }
		}
	}

	internal suspend fun afterInitialScan(result: CustomSoundPack.ScanResult) {
		if (!result.published || result.count == 0) return
		withContext(client) { beginReload(result, report = false) }
	}

	private fun beginReload(result: CustomSoundPack.ScanResult, report: Boolean) {
		var future: CompletableFuture<Void>? = null
		try {
			if (!CustomSoundPack.runIfCurrent(result) { future = reloadResources() }) return
		} catch (exception: Exception) {
			log.warn("Could not start a custom sound resource reload", exception)
			if (report) notify("Could not reload custom sounds: ${exception.reason()}.")
			return
		}
		future!!.whenComplete { _, failure ->
			scope.launch(client) {
				if (failure == null) {
					if (report) reportScan(result)
				} else {
					log.warn("Custom sound resource reload failed", failure)
					if (report) notify("Could not reload custom sounds: ${failure.message ?: failure.javaClass.simpleName}.")
				}
			}
		}
	}

	private fun reportScan(result: CustomSoundPack.ScanResult) {
		val failure = result.failure
		if (failure == null) {
			notify("Read ${result.count} custom sounds.")
		} else {
			notify("Could not read custom sounds: ${failure.reason()}. Previous custom sounds were cleared.")
		}
	}

	private companion object {
		val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
