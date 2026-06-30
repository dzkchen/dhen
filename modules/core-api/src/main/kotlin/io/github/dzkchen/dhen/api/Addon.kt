package io.github.dzkchen.dhen.api

// Every runtime side effect returns a handle so the lifecycle manager can dispose it.
interface RegistrationHandle {
	val id: String
	fun dispose()
}

interface AddonLogger {
	fun info(message: String)
	fun warn(message: String)
	fun error(message: String, throwable: Throwable? = null)
}

interface DhenAddon {
	val metadata: AddonMetadata

	fun register(context: AddonContext)
}

interface DhenModule {
	val metadata: ModuleMetadata

	fun onEnable(context: ModuleEnableContext) {}

	fun onDisable(context: ModuleDisableContext) {}
}

interface AddonContext {
	val addonId: AddonId
	val logger: AddonLogger

	fun registerModule(module: DhenModule)
}

// Context handed to a module when it is enabled. Modules ask for platform behavior through
// these methods instead of owning Fabric setup directly. Every registration returns a handle
// the runtime tracks and disposes on disable. Called on the client thread.
interface ModuleEnableContext {
	val moduleId: ModuleId
	val logger: AddonLogger

	fun booleanSetting(settingId: SettingId): Boolean

	fun booleanSetting(settingId: String): Boolean = booleanSetting(SettingId(settingId))

	fun onKeybind(keybindId: KeybindId, handler: () -> Unit): RegistrationHandle

	fun onKeybind(keybindId: String, handler: () -> Unit): RegistrationHandle = onKeybind(KeybindId(keybindId), handler)

	fun addHudText(widgetId: WidgetId, provider: () -> String?): RegistrationHandle

	fun addHudText(widgetId: String, provider: () -> String?): RegistrationHandle = addHudText(WidgetId(widgetId), provider)

	fun sendChat(message: String)
}

interface ModuleDisableContext {
	val moduleId: ModuleId
	val logger: AddonLogger

	fun sendChat(message: String)
}
