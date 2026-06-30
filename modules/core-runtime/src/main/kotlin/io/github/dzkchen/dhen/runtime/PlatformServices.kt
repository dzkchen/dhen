package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.KeybindSpec
import io.github.dzkchen.dhen.api.RegistrationHandle
import java.nio.file.Path

// A physical keybind the platform must register at startup. The id is namespaced per module
// so the runtime can bind/unbind the active handler without colliding across modules.
data class PlatformKeybind(
	val id: String,
	val spec: KeybindSpec,
)

// Implemented by platform-fabric. core-runtime stays free of Fabric/Minecraft so it can be
// unit tested without launching the client. The platform owns physical registration (keybinds,
// HUD render hook, chat) and JSON/file serialization; the runtime decides when to activate them.
interface PlatformServices {
	val configDir: Path
	val jsonCodec: JsonCodec

	fun logger(name: String): AddonLogger

	// Registers the physical keybinds known after addon discovery. The platform polls them and
	// invokes whichever handler the runtime has currently bound (none when the module is disabled).
	fun registerKeybinds(keybinds: List<PlatformKeybind>)

	// Binds the active handler for an already-registered physical keybind. Dispose unbinds it.
	fun bindKeybindHandler(keybindId: String, handler: () -> Unit): RegistrationHandle

	// Adds a HUD text widget rendered each frame from the provider (null = render nothing).
	fun addHudWidget(widgetId: String, provider: () -> String?): RegistrationHandle

	fun sendChat(message: String)
}

// Minimal JSON bridge so core-runtime does not carry a serialization dependency. The platform
// supplies a concrete codec (Gson from the Minecraft classpath). Values are plain maps, lists,
// strings, booleans, and numbers.
interface JsonCodec {
	fun encode(value: Any?): String

	fun decode(text: String): Any?
}
