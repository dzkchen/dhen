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
	val client: ClientContext get() = NoClientContext
	val rawMinecraft: RawMinecraftBridge get() = client.rawMinecraft
	val rawFabric: RawFabricBridge get() = client.rawFabric

	fun registerModule(module: DhenModule)
}

interface ClientContext {
	val player: PlayerContext?
	val world: WorldContext?
	val chat: ChatContext
	val rawMinecraft: RawMinecraftBridge
	val rawFabric: RawFabricBridge
}

interface PlayerContext {
	val name: String?
	val raw: Any?
}

interface WorldContext {
	val name: String?
	val raw: Any?
}

interface ChatContext {
	fun sendSystemMessage(message: String)
}

interface TooltipContext {
	fun addLine(text: String)
}

interface HudRenderContext {
	fun addText(widgetId: WidgetId, provider: () -> String?): RegistrationHandle

	fun addText(widgetId: String, provider: () -> String?): RegistrationHandle = addText(WidgetId(widgetId), provider)
}

interface RawMinecraftBridge {
	val client: Any?
	val player: Any?
	val world: Any?
}

interface RawFabricBridge {
	val loader: Any?
}

// Context handed to a module when it is enabled. Modules ask for platform behavior through
// these methods instead of owning Fabric setup directly. Every registration returns a handle
// the runtime tracks and disposes on disable. Called on the client thread.
interface ModuleEnableContext {
	val moduleId: ModuleId
	val logger: AddonLogger
	val client: ClientContext get() = NoClientContext
	val chat: ChatContext get() = client.chat
	val hudRender: HudRenderContext get() = NoHudRenderContext
	val rawMinecraft: RawMinecraftBridge get() = client.rawMinecraft
	val rawFabric: RawFabricBridge get() = client.rawFabric

	fun booleanSetting(settingId: SettingId): Boolean

	fun booleanSetting(settingId: String): Boolean = booleanSetting(SettingId(settingId))

	fun onKeybind(keybindId: KeybindId, handler: () -> Unit): RegistrationHandle

	fun onKeybind(keybindId: String, handler: () -> Unit): RegistrationHandle = onKeybind(KeybindId(keybindId), handler)

	fun addHudText(widgetId: WidgetId, provider: () -> String?): RegistrationHandle

	fun addHudText(widgetId: String, provider: () -> String?): RegistrationHandle = addHudText(WidgetId(widgetId), provider)

	fun sendChat(message: String) = chat.sendSystemMessage(message)
}

interface ModuleDisableContext {
	val moduleId: ModuleId
	val logger: AddonLogger
	val client: ClientContext get() = NoClientContext
	val chat: ChatContext get() = client.chat
	val rawMinecraft: RawMinecraftBridge get() = client.rawMinecraft
	val rawFabric: RawFabricBridge get() = client.rawFabric

	fun sendChat(message: String) = chat.sendSystemMessage(message)
}

object NoRawMinecraftBridge : RawMinecraftBridge {
	override val client: Any? = null
	override val player: Any? = null
	override val world: Any? = null
}

object NoRawFabricBridge : RawFabricBridge {
	override val loader: Any? = null
}

object NoChatContext : ChatContext {
	override fun sendSystemMessage(message: String) = Unit
}

object NoHudRenderContext : HudRenderContext {
	override fun addText(widgetId: WidgetId, provider: () -> String?): RegistrationHandle = NoopRegistrationHandle("hud:${widgetId.value}")
}

object NoClientContext : ClientContext {
	override val player: PlayerContext? = null
	override val world: WorldContext? = null
	override val chat: ChatContext = NoChatContext
	override val rawMinecraft: RawMinecraftBridge = NoRawMinecraftBridge
	override val rawFabric: RawFabricBridge = NoRawFabricBridge
}

private class NoopRegistrationHandle(override val id: String) : RegistrationHandle {
	override fun dispose() = Unit
}
