package io.github.dzkchen.dhen

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.command.CommandRegistry
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.gui.ClickGuiLayout
import io.github.dzkchen.dhen.gui.ClickGuiScreen
import io.github.dzkchen.dhen.gui.PanelState
import io.github.dzkchen.dhen.input.InputRuntime
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.module.PlaceholderModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object Dhen : ClientModInitializer {
	const val MOD_ID: String = "dhen"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	val modules: ModuleManager = ModuleManager()
	private val inputRuntime = InputRuntime(modules.eventBus)

	private val commands = CommandRegistry<FabricClientCommandSource>(modules) { source, message ->
		source.sendFeedback(Component.literal(message))
	}

	private val configScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private lateinit var coreStore: ConfigStore
	private lateinit var moduleStore: ConfigStore
	private lateinit var panelLayout: MutableMap<String, PanelState>

	override fun onInitializeClient() {
		coreStore = ConfigStore(FabricLoader.getInstance().configDir.resolve("$MOD_ID/core.json"), configScope)
		moduleStore = ConfigStore(
			FabricLoader.getInstance().configDir.resolve("$MOD_ID/modules.json"),
			configScope,
			ModulePersistence.migrations
		)
		panelLayout = ClickGuiLayout.read(coreStore.load())
		modules.registerAll(
			PlaceholderModule(),
			PlaceholderModule(
				name = "Test Overlay",
				category = Category.VISUAL,
				description = "Second placeholder for search and keyboard navigation.",
				toggleKey = GLFW.GLFW_KEY_UNKNOWN
			),
			PlaceholderModule(
				name = "Sample Timer",
				category = Category.COMBAT,
				description = "Third placeholder; matches a search on its description only.",
				toggleKey = GLFW.GLFW_KEY_UNKNOWN
			)
		)
		ModulePersistence.apply(modules, moduleStore.load())
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> commands.install(dispatcher) }

		val openGuiKey = KeyMappingHelper.registerKeyMapping(
			KeyMapping(
				"key.dhen.open_gui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyMapping.Category.MISC
			)
		)
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			modules.clientDispatcher.drainQueue()
			if (client.gui.screen() !is ClickGuiScreen) inputRuntime.poll(InputRuntime.Glfw, client.window.handle())
			if (openGuiKey.consumeClick()) {
				client.gui.setScreen(
					ClickGuiScreen(
						Category.entries.toList(),
						modules,
						panelLayout,
						persistLayout = { coreStore.save(ClickGuiLayout.write(panelLayout)) },
						persistModules = { moduleStore.save(ModulePersistence.snapshot(modules)) }
					)
				)
			}
		}
		LOGGER.info("Dhen initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
