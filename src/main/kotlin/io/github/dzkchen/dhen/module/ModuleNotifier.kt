package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

fun interface ModuleNotifier {
	fun notify(module: Module, message: String)

	companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		val LogBacked = ModuleNotifier { _, message -> log.warn(message) }

		fun chatBacked(clientThread: Executor, announce: (String) -> Unit) = ModuleNotifier { _, message ->
			log.warn(message)
			clientThread.execute { announce(message) }
		}
	}
}
