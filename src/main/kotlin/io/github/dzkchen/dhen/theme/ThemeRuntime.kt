package io.github.dzkchen.dhen.theme

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.command.ThemeCommands
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.DhenTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

internal class ThemeRuntime(
	private val root: Path,
	private val scope: CoroutineScope,
	private val client: CoroutineContext,
	private val persist: () -> Unit,
	private val chat: (String) -> Unit,
	private val reveal: (Path) -> Unit
) : ThemeCommands {

	init {
		ClientPrefs.reload.value = { reload(chat) }
		ClientPrefs.browse.value = { browse(chat) }
	}

	override fun names(): List<String> = ThemeStore.ids

	override fun summary(): String {
		val active = ClientPrefs.theme.value
		return ThemeStore.themes.joinToString(", ", "Themes: ", ".") { entry ->
			if (entry.id.equals(active, ignoreCase = true)) "${entry.id} (active)" else entry.id
		}
	}

	override fun select(name: String): String {
		val entry = ThemeStore.find(name) ?: return "No theme named '$name'. Try /dhen theme list."
		ClientPrefs.theme.value = entry.id
		ClientPrefs.sync()
		persist()
		return "Theme set to '${entry.id}'."
	}

	override fun reload(notify: (String) -> Unit) = offThread(
		notify,
		"Could not read ${ThemeStore.DIRECTORY}",
		{ ThemeStore.refresh(root).size }
	) { count -> "Read $count themes; drawing '${ClientPrefs.theme.value}'." }

	override fun export(name: String?, notify: (String) -> Unit) {
		val requested = name ?: ClientPrefs.theme.value
		if (!ThemeStore.legal(requested)) {
			notify("'$requested' cannot name a theme folder; use up to ${ThemeStore.MAX_NAME} letters, digits, '-' or '_'.")
			return
		}
		val metadata = ThemeStore.find(ClientPrefs.theme.value)
		val resolved = DhenTheme.active
		offThread(
			notify,
			"Could not write ${ThemeStore.DIRECTORY}/$requested/${ThemeFormat.MANIFEST}",
			{
				val folder = ThemeExport.write(root, requested, metadata, resolved)
				ThemeStore.refresh(root)
				folder
			}
		) { folder -> "Exported the theme on screen to ${ThemeStore.DIRECTORY}/${folder.fileName}/${ThemeFormat.MANIFEST}." }
	}

	fun browse(notify: (String) -> Unit = {}) {
		val failure = "Could not open ${ThemeStore.DIRECTORY}"
		scope.launch {
			val folder = try {
				Files.createDirectories(root.resolve(ThemeStore.DIRECTORY))
			} catch (e: Exception) {
				log.warn("{} under {}", failure, root, e)
				withContext(client) { notify("$failure: ${reason(e)}") }
				return@launch
			}
			withContext(client) { open(folder, notify) }
		}
	}

	private fun <T> offThread(notify: (String) -> Unit, failure: String, work: () -> T, say: (T) -> String) {
		scope.launch {
			val done = try {
				work()
			} catch (e: Exception) {
				log.warn("{} under {}", failure, root, e)
				withContext(client) { notify("$failure: ${reason(e)}") }
				return@launch
			}
			withContext(client) {
				ClientPrefs.adopt()
				notify(say(done))
			}
		}
	}

	private fun open(folder: Path, notify: (String) -> Unit) {
		try {
			reveal(folder)
		} catch (e: Exception) {
			log.warn("Could not show the themes folder {}", folder, e)
			notify("Could not open ${ThemeStore.DIRECTORY}: ${reason(e)}")
		}
	}

	private fun reason(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

	private companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	}
}
