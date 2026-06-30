package io.github.dzkchen.dhen.runtime

import com.google.gson.Gson
import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.RegistrationHandle
import java.nio.file.Path

// In-memory platform used by runtime unit tests. Tracks bound keybind handlers and HUD widgets so
// tests can assert handle disposal, and persists config to a real temp directory via Gson.
class FakePlatformServices(override val configDir: Path) : PlatformServices {
	val keybindHandlers = HashMap<String, () -> Unit>()
	val hudWidgets = HashMap<String, () -> String?>()
	val chatMessages = ArrayList<String>()
	var registeredKeybinds: List<PlatformKeybind> = emptyList()

	override val jsonCodec: JsonCodec = object : JsonCodec {
		private val gson = Gson()
		override fun encode(value: Any?): String = gson.toJson(value)
		override fun decode(text: String): Any? = gson.fromJson(text, Any::class.java)
	}

	override fun logger(name: String): AddonLogger = object : AddonLogger {
		override fun info(message: String) {}
		override fun warn(message: String) {}
		override fun error(message: String, throwable: Throwable?) {}
	}

	override fun registerKeybinds(keybinds: List<PlatformKeybind>) {
		registeredKeybinds = keybinds
	}

	override fun bindKeybindHandler(keybindId: String, handler: () -> Unit): RegistrationHandle {
		keybindHandlers[keybindId] = handler
		return handle("keybind:$keybindId") { keybindHandlers.remove(keybindId) }
	}

	override fun addHudWidget(widgetId: String, provider: () -> String?): RegistrationHandle {
		hudWidgets[widgetId] = provider
		return handle("hud:$widgetId") { hudWidgets.remove(widgetId) }
	}

	override fun sendChat(message: String) {
		chatMessages.add(message)
	}

	private fun handle(id: String, onDispose: () -> Unit) = object : RegistrationHandle {
		override val id: String = id
		override fun dispose() = onDispose()
	}
}
