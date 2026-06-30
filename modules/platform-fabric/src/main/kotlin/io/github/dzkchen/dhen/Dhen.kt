package io.github.dzkchen.dhen

import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.platform.CommandBridge
import io.github.dzkchen.dhen.platform.FabricPlatformServices
import io.github.dzkchen.dhen.runtime.DhenRuntime
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object Dhen : ClientModInitializer {
	const val MOD_ID: String = "dhen"

	// Fabric entrypoint key native addons register under in their fabric.mod.json.
	private const val ADDON_ENTRYPOINT: String = "dhen"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	// Set during client init; read by the Mod Menu screen factory and commands.
	lateinit var runtime: DhenRuntime
		private set

	override fun onInitializeClient() {
		LOGGER.info("Dhen client initializing")

		val platform = FabricPlatformServices()
		runtime = DhenRuntime(platform)

		discoverAddons().forEach { runtime.registerAddon(it) }

		runtime.start()
		CommandBridge.register(runtime)

		val diagnostics = runtime.diagnostics()
		LOGGER.info(
			"Dhen ready: {} addon(s), {} module(s), {} active handle(s)",
			diagnostics.addons.size,
			diagnostics.modules.size,
			diagnostics.totalActiveHandles,
		)
	}

	// Reads native addon JAR entrypoints already loaded by Fabric and hands instances to the runtime.
	private fun discoverAddons(): List<DhenAddon> {
		val containers = FabricLoader.getInstance().getEntrypointContainers(ADDON_ENTRYPOINT, DhenAddon::class.java)
		val addons = ArrayList<DhenAddon>(containers.size)
		for (container in containers) {
			val modId = container.provider.metadata.id
			try {
				val addon = container.entrypoint
				LOGGER.info("Discovered Dhen addon '{}' from mod '{}'", addon.metadata.id, modId)
				addons.add(addon)
			} catch (t: Throwable) {
				LOGGER.error("Failed to load Dhen addon entrypoint from mod '{}'", modId, t)
			}
		}
		return addons
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
