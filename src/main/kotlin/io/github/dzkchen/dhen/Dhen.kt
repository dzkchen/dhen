package io.github.dzkchen.dhen

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.command.CommandRegistry
import io.github.dzkchen.dhen.gui.FlatPrimitivesDemoScreen
import io.github.dzkchen.dhen.input.InputRuntime
import io.github.dzkchen.dhen.module.ModuleManager
import io.github.dzkchen.dhen.module.PlaceholderModule
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
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

	override fun onInitializeClient() {
		modules.register(PlaceholderModule())
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
			inputRuntime.poll(InputRuntime.Glfw, client.window.handle())
			if (openGuiKey.consumeClick()) {
				client.gui.setScreen(FlatPrimitivesDemoScreen())
			}
		}
		LOGGER.info("Dhen initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
