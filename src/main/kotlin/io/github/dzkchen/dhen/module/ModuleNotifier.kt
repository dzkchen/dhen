package io.github.dzkchen.dhen.module

import org.slf4j.LoggerFactory

fun interface ModuleNotifier {
	fun notify(module: Module, message: String)

	companion object {
		private val logger = LoggerFactory.getLogger(ModuleNotifier::class.java)
		val LogBacked = ModuleNotifier { _, message -> logger.warn(message) }
	}
}
