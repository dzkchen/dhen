package io.github.dzkchen.dhen

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.command.CommandRegistry
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.config.ModulePersistence
import io.github.dzkchen.dhen.gui.ClickGuiLayout
import io.github.dzkchen.dhen.gui.ClickGuiScreen
import io.github.dzkchen.dhen.gui.Effects
import io.github.dzkchen.dhen.gui.PanelState
import io.github.dzkchen.dhen.input.InputRuntime
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.module.PlaceholderModule
import io.github.dzkchen.dhen.ui.hud.HudAnchor
import io.github.dzkchen.dhen.ui.hud.HudEditorScreen
import io.github.dzkchen.dhen.ui.hud.HudRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement as FabricHudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object Dhen : ClientModInitializer {
	const val MOD_ID: String = "dhen"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	val modules: ModuleManager = ModuleManager()
	private val inputRuntime = InputRuntime(modules.eventBus)
	private val hudRuntime = HudRuntime(modules)

	private val commands = CommandRegistry<FabricClientCommandSource>(modules, ::openHudEditor, ::persistCore) { source, message ->
		source.sendFeedback(Component.literal(message))
	}

	private val configScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private lateinit var coreStore: ConfigStore
	private lateinit var moduleStore: ConfigStore
	private lateinit var panelLayout: MutableMap<String, PanelState>
	private var hudEditorRequested = false

	override fun onInitializeClient() {
		coreStore = ConfigStore(FabricLoader.getInstance().configDir.resolve("$MOD_ID/core.json"), configScope)
		moduleStore = ConfigStore(
			FabricLoader.getInstance().configDir.resolve("$MOD_ID/modules.json"),
			configScope,
			ModulePersistence.migrations
		)
		val core = coreStore.load()
		panelLayout = ClickGuiLayout.read(core)
		Effects.read(core)
		modules.registerAll(
			PlaceholderModule(),
			PlaceholderModule(
				name = "Test Overlay",
				category = Category.VISUAL,
				description = "Second placeholder for search and keyboard navigation.",
				toggleKey = GLFW.GLFW_KEY_UNKNOWN,
				hudAnchor = HudAnchor.TOP_RIGHT
			),
			PlaceholderModule(
				name = "Sample Timer",
				category = Category.COMBAT,
				description = "Third placeholder; matches a search on its description only.",
				toggleKey = GLFW.GLFW_KEY_UNKNOWN,
				hudAnchor = HudAnchor.BOTTOM_RIGHT
			)
		)
		ModulePersistence.apply(modules, moduleStore.load())
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> commands.install(dispatcher) }
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.SUBTITLES,
			id("hud"),
			FabricHudElement { graphics, _ -> hudRuntime.render(graphics, Minecraft.getInstance().font) }
		)
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
			id("hud_measurements"),
			ResourceManagerReloadListener { hudRuntime.invalidateMeasurements() }
		)

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
			if (!ownsKeyboard(client.gui.screen())) inputRuntime.poll(InputRuntime.Glfw, client.window.handle())
			if (openGuiKey.consumeClick()) client.gui.setScreen(clickGuiScreen())
			if (hudEditorRequested) {
				hudEditorRequested = false
				client.gui.setScreen(HudEditorScreen(modules, ::persistModules))
			}
		}
		LOGGER.info("Dhen initialized")
	}

	private fun ownsKeyboard(screen: Screen?): Boolean =
		screen is ClickGuiScreen || screen is HudEditorScreen

	private fun openHudEditor() {
		hudEditorRequested = true
	}

	private fun persistModules() {
		moduleStore.save(ModulePersistence.snapshot(modules))
	}

	private fun persistCore() {
		coreStore.save(Effects.writeInto(ClickGuiLayout.write(panelLayout)))
	}

	internal fun clickGuiScreen(parent: Screen? = null): Screen = ClickGuiScreen(
		Category.entries.toList(),
		modules,
		panelLayout,
		persistCore = ::persistCore,
		persistModules = ::persistModules,
		parent = parent
	)

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
