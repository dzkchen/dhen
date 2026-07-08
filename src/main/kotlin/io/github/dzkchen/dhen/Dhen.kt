package io.github.dzkchen.dhen

import net.fabricmc.api.ClientModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object Dhen : ClientModInitializer {
	const val MOD_ID: String = "dhen"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitializeClient() {
		LOGGER.info("Dhen initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
