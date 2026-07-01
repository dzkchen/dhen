package io.github.dzkchen.dhen

import io.github.dzkchen.dhen.api.DhenAddon
import io.github.dzkchen.dhen.platform.CommandBridge
import io.github.dzkchen.dhen.platform.FabricEventAdapters
import io.github.dzkchen.dhen.platform.FabricPlatformServices
import io.github.dzkchen.dhen.runtime.AddonSource
import io.github.dzkchen.dhen.runtime.AddonSourceType
import io.github.dzkchen.dhen.runtime.DhenRuntime
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
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

		registerDiscoveredAddons()

		runtime.start()
		CommandBridge.register(runtime)
		FabricEventAdapters.register(runtime)

		val diagnostics = runtime.diagnostics()
		LOGGER.info(
			"Dhen ready: {} addon(s), {} module(s), {} active handle(s)",
			diagnostics.addons.size,
			diagnostics.modules.size,
			diagnostics.totalActiveHandles,
		)
		for (module in diagnostics.modules) {
			LOGGER.info("  module {} [{}] state={}", module.moduleId, module.addonId, module.state)
		}
	}

	// Reads native addon JAR entrypoints already loaded by Fabric and hands instances to the runtime,
	// tagging each with the loaded-JAR source so diagnostics can show where it came from.
	private fun registerDiscoveredAddons() {
		val containers = FabricLoader.getInstance().getEntrypointContainers(ADDON_ENTRYPOINT, DhenAddon::class.java)
		for (container in containers) {
			val provider = container.provider
			val modId = provider.metadata.id
			try {
				val addon = container.entrypoint
				LOGGER.info("Discovered Dhen addon '{}' from mod '{}'", addon.metadata.id, modId)
				runtime.registerAddon(addon, AddonSource(AddonSourceType.LOADED_JAR, sourceLocation(provider, modId)))
			} catch (t: Throwable) {
				LOGGER.error("Failed to load Dhen addon entrypoint from mod '{}'", modId, t)
			}
		}
	}

	// The on-disk paths Fabric loaded the addon from, falling back to the mod id when the origin
	// is not a plain path (for example a nested jar) or is otherwise unavailable.
	private fun sourceLocation(provider: ModContainer, modId: String): String =
		try {
			provider.origin.paths.joinToString(", ") { it.fileName.toString() }.ifBlank { modId }
		} catch (t: Throwable) {
			modId
		}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
