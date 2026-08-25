package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.gui.DhenType
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

fun interface ModuleNotifier {
	fun notify(module: Module, message: String, copyText: String?)

	companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		val LogBacked = ModuleNotifier { _, message, _ -> log.warn(message) }

		fun chatBacked(clientThread: Executor, announce: (Component) -> Unit) = ModuleNotifier { _, message, copyText ->
			log.warn(message)
			clientThread.execute {
				val component = if (copyText == null) {
					DhenType.overWorld(message)
				} else {
					DhenType.copyableOverWorld(message, copyText)
				}
				announce(component)
			}
		}
	}
}
