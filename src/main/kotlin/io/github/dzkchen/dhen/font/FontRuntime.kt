package io.github.dzkchen.dhen.font

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.util.reason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

internal class FontRuntime(
	root: Path,
	private val scope: CoroutineScope,
	private val client: CoroutineContext,
	private val persist: () -> Unit,
	private val fontChanged: () -> Unit,
	chat: (String) -> Unit,
	private val reveal: (Path) -> Unit
) {

	private val choosing = AtomicBoolean()

	init {
		FontStore.install(root)
		ClientPrefs.reloadFonts.value = { rescan(chat, rebuild = true) }
		ClientPrefs.addFont.value = { add(chat) }
		ClientPrefs.browseFonts.value = { browse(chat) }
	}

	fun prime() = rescan({}, rebuild = false)

	private fun rescan(notify: (String) -> Unit, rebuild: Boolean) {
		scope.launch {
			val count = if (rebuild) FontStore.refresh().size else FontStore.faces().size
			withContext(client) {
				if (rebuild) afterReload { settle(count, notify) } else settle(count, notify)
			}
		}
	}

	private fun add(notify: (String) -> Unit) {
		if (!choosing.compareAndSet(false, true)) return
		scope.launch {
			val chosen = choose(notify) ?: return@launch
			val added = try {
				FontStore.importFrom(chosen)
			} catch (e: Exception) {
				log.warn("Could not add the font {}", chosen, e)
				withContext(client) { notify("Could not add that font: ${e.reason()}.") }
				return@launch
			}
			FontStore.refresh()
			withContext(client) { afterReload { failed -> adopt(added, failed, notify) } }
		}
	}

	private suspend fun choose(notify: (String) -> Unit): Path? = try {
		FontChooser.pick()
	} catch (e: Exception) {
		log.warn("Could not open a font chooser", e)
		withContext(client) {
			notify("Could not open a file chooser; drop the file into ${FontStore.DIRECTORY} and press Reload fonts.")
		}
		null
	} finally {
		choosing.set(false)
	}

	private fun browse(notify: (String) -> Unit) {
		scope.launch {
			val folder = try {
				FontStore.folder()
			} catch (e: Exception) {
				log.warn("Could not make the fonts folder", e)
				withContext(client) { notify("Could not open the fonts folder: ${e.reason()}.") }
				return@launch
			}
			withContext(client) { open(folder, notify) }
		}
	}

	private fun open(folder: Path, notify: (String) -> Unit) {
		try {
			reveal(folder)
		} catch (e: Exception) {
			log.warn("Could not show the fonts folder {}", folder, e)
			notify("Could not open your file browser; the fonts folder is $folder.")
		}
	}

	private fun adopt(added: String, failed: Boolean, notify: (String) -> Unit) {
		if (failed) {
			notify("Copied '$added' into ${FontStore.DIRECTORY}, but the reload failed; press Reload fonts to try again.")
			return
		}
		ClientPrefs.font.value = added
		ClientPrefs.dhenFont.on = true
		if (ClientPrefs.adopt()) fontChanged()
		persist()
		notify("Added '$added'; Dhen and the game's default text draw in it now.")
	}

	private fun settle(count: Int, notify: (String) -> Unit) {
		if (ClientPrefs.adopt()) fontChanged()
		notify("Read $count fonts; drawing '${ClientPrefs.font.value}'.")
	}

	private fun afterReload(settle: (Boolean) -> Unit) {
		val minecraft = Minecraft.getInstance()
		minecraft.reloadResourcePacks().handle { _, error -> minecraft.execute { settle(error != null) } }
	}

	private companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
