package io.github.dzkchen.dhen.module

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory

fun interface ModuleNotifier {
	fun notify(module: Module, message: String)

	companion object {
		private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
		val LogBacked = ModuleNotifier { _, message -> log.warn(message) }
	}
}
