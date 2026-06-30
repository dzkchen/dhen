package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.ChatContext
import io.github.dzkchen.dhen.api.ClientContext
import io.github.dzkchen.dhen.api.HudRenderContext
import io.github.dzkchen.dhen.api.KeybindId
import io.github.dzkchen.dhen.api.ModuleDisableContext
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.RegistrationHandle
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.WidgetId

// Builds the keybind id the platform registers for a module's keybind. Both the physical
// registration and the per-enable handler binding must agree on this format.
internal fun platformKeybindId(moduleId: ModuleId, keybindId: KeybindId): String = "${moduleId.value}/${keybindId.value}"

internal fun platformKeybindId(moduleId: ModuleId, keybindId: String): String = platformKeybindId(moduleId, KeybindId(keybindId))

internal fun platformWidgetId(moduleId: ModuleId, widgetId: WidgetId): String = "${moduleId.value}/${widgetId.value}"

private class EnableContextImpl(
	private val record: ModuleRecord,
	private val platform: PlatformServices,
	private val config: ConfigManager,
	override val logger: AddonLogger,
) : ModuleEnableContext {
	override val moduleId: ModuleId get() = record.id
	override val client: ClientContext get() = platform.clientContext
	override val chat: ChatContext = object : ChatContext {
		override fun sendSystemMessage(message: String) = platform.sendChat(message)
	}
	override val hudRender: HudRenderContext = object : HudRenderContext {
		override fun addText(widgetId: WidgetId, provider: () -> String?): RegistrationHandle =
			track(platform.addHudWidget(platformWidgetId(record.id, widgetId), provider))
	}

	override fun booleanSetting(settingId: SettingId): Boolean = config.getBoolean(record.addonId, settingId)

	override fun onKeybind(keybindId: KeybindId, handler: () -> Unit): RegistrationHandle =
		track(platform.bindKeybindHandler(platformKeybindId(record.id, keybindId), handler))

	override fun addHudText(widgetId: WidgetId, provider: () -> String?): RegistrationHandle =
		hudRender.addText(widgetId, provider)

	private fun track(handle: RegistrationHandle): RegistrationHandle {
		record.handles.addLast(handle)
		return handle
	}
}

private class DisableContextImpl(
	private val record: ModuleRecord,
	private val platform: PlatformServices,
	override val logger: AddonLogger,
) : ModuleDisableContext {
	override val moduleId: ModuleId get() = record.id
	override val client: ClientContext get() = platform.clientContext
	override val chat: ChatContext = object : ChatContext {
		override fun sendSystemMessage(message: String) = platform.sendChat(message)
	}
}

// Drives a module between ENABLED and DISABLED. Enable rolls back any registrations it created if
// onEnable throws; disable always disposes handles (in reverse order) even if onDisable throws.
class LifecycleManager(
	private val platform: PlatformServices,
	private val config: ConfigManager,
	private val logger: AddonLogger,
) {
	fun enable(record: ModuleRecord) {
		if (record.state == LifecycleState.ENABLED) return
		val context = EnableContextImpl(record, platform, config, logger)
		try {
			record.module.onEnable(context)
			record.state = LifecycleState.ENABLED
			record.failureReason = null
			record.lastTransition = "enabled"
		} catch (t: Throwable) {
			logger.error("Failed to enable module ${record.id}", t)
			disposeHandles(record)
			record.state = LifecycleState.FAILED
			record.failureReason = t.message ?: t.javaClass.simpleName
			record.lastTransition = "enable-failed"
		}
	}

	fun disable(record: ModuleRecord) {
		if (record.state != LifecycleState.ENABLED && record.state != LifecycleState.FAILED) {
			record.state = LifecycleState.DISABLED
			record.lastTransition = "disabled"
			return
		}
		try {
			record.module.onDisable(DisableContextImpl(record, platform, logger))
		} catch (t: Throwable) {
			logger.error("onDisable threw for module ${record.id}; continuing cleanup", t)
		}
		disposeHandles(record)
		record.state = LifecycleState.DISABLED
		record.failureReason = null
		record.lastTransition = "disabled"
	}

	// Disposes handles in reverse registration order. A throwing handle does not stop the rest.
	private fun disposeHandles(record: ModuleRecord) {
		while (record.handles.isNotEmpty()) {
			val handle = record.handles.removeLast()
			try {
				handle.dispose()
			} catch (t: Throwable) {
				logger.error("Failed to dispose handle ${handle.id} for module ${record.id}", t)
			}
		}
	}
}
