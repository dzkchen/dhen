package io.github.dzkchen.dhen.platform

import com.mojang.blaze3d.platform.InputConstants
import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.RegistrationHandle
import io.github.dzkchen.dhen.runtime.JsonCodec
import io.github.dzkchen.dhen.runtime.PlatformKeybind
import io.github.dzkchen.dhen.runtime.PlatformServices
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.nio.file.Path

private const val HUD_TEXT_COLOR: Int = -0x1

class FabricPlatformServices : PlatformServices {
	override val configDir: Path = FabricLoader.getInstance().configDir
	override val jsonCodec: JsonCodec = GsonJsonCodec

	private val keyMappings = LinkedHashMap<String, KeyMapping>()
	private val keybindHandlers = HashMap<String, () -> Unit>()

	private val hudWidgets = LinkedHashMap<String, () -> String?>()
	private val hudLock = Any()
	private var hudRegistered = false

	override fun logger(name: String): AddonLogger = Slf4jAddonLogger(name)

	override fun registerKeybinds(keybinds: List<PlatformKeybind>) {
		for (kb in keybinds) {
			val mapping = KeyMappingHelper.registerKeyMapping(
				KeyMapping(kb.spec.displayName, InputConstants.Type.KEYSYM, kb.spec.defaultKey, KeyMapping.Category.MISC),
			)
			keyMappings[kb.id] = mapping
		}
		ClientTickEvents.END_CLIENT_TICK.register(
			ClientTickEvents.EndTick { _ ->
				for ((id, mapping) in keyMappings) {
					while (mapping.consumeClick()) {
						keybindHandlers[id]?.invoke()
					}
				}
			},
		)
	}

	override fun bindKeybindHandler(keybindId: String, handler: () -> Unit): RegistrationHandle {
		keybindHandlers[keybindId] = handler
		return SimpleHandle("keybind:$keybindId") {
			if (keybindHandlers[keybindId] === handler) keybindHandlers.remove(keybindId)
		}
	}

	override fun addHudWidget(widgetId: String, provider: () -> String?): RegistrationHandle {
		ensureHudRegistered()
		synchronized(hudLock) { hudWidgets[widgetId] = provider }
		return SimpleHandle("hud:$widgetId") {
			synchronized(hudLock) { hudWidgets.remove(widgetId) }
		}
	}

	override fun sendChat(message: String) {
		val client = Minecraft.getInstance()
		client.execute { client.player?.sendSystemMessage(Component.literal(message)) }
	}

	private fun ensureHudRegistered() {
		if (hudRegistered) return
		hudRegistered = true
		val element = HudElement { graphics, _ ->
			val client = Minecraft.getInstance()
			val snapshot = synchronized(hudLock) { hudWidgets.values.toList() }
			var y = 4
			for (provider in snapshot) {
				val text = try {
					provider()
				} catch (t: Throwable) {
					null
				} ?: continue
				graphics.text(client.font, Component.literal(text), 4, y, HUD_TEXT_COLOR)
				y += 10
			}
		}
		HudElementRegistry.addLast(Dhen.id("hud"), element)
	}
}

private class SimpleHandle(override val id: String, private val onDispose: () -> Unit) : RegistrationHandle {
	private var disposed = false

	override fun dispose() {
		if (disposed) return
		disposed = true
		onDispose()
	}
}

private class Slf4jAddonLogger(name: String) : AddonLogger {
	private val logger = LoggerFactory.getLogger(name)

	override fun info(message: String) = logger.info(message)

	override fun warn(message: String) = logger.warn(message)

	override fun error(message: String, throwable: Throwable?) {
		if (throwable != null) logger.error(message, throwable) else logger.error(message)
	}
}
