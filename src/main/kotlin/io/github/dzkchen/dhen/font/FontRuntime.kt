package io.github.dzkchen.dhen.font

import io.github.dzkchen.dhen.gui.ClientPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

internal class FontRuntime(
	root: Path,
	private val scope: CoroutineScope,
	private val client: CoroutineContext,
	private val fontChanged: () -> Unit,
	chat: (String) -> Unit
) {

	init {
		FontStore.install(root)
		ClientPrefs.reloadFonts.value = { rescan(chat, rebuild = true) }
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

	private fun settle(count: Int, notify: (String) -> Unit) {
		if (ClientPrefs.adopt()) fontChanged()
		notify("Read $count fonts; drawing '${ClientPrefs.font.value}'.")
	}

	private fun afterReload(settle: () -> Unit) {
		val minecraft = Minecraft.getInstance()
		minecraft.reloadResourcePacks().handle { _, _ -> minecraft.execute(settle) }
	}
}
