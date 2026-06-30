package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.ModuleDisableContext
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.RegistrationHandle

// Builds the keybind id the platform registers for a module's keybind. Both the physical
// registration and the per-enable handler binding must agree on this format.
internal fun platformKeybindId(moduleId: ModuleId, keybindId: String): String = "${moduleId.value}/$keybindId"

internal fun platformWidgetId(moduleId: ModuleId, widgetId: String): String = "${moduleId.value}/$widgetId"

private class EnableContextImpl(
	private val record: ModuleRecord,
	private val platform: PlatformServices,
	private val config: ConfigManager,
	override val logger: AddonLogger,
) : ModuleEnableContext {
	override val moduleId: ModuleId get() = record.id

	override fun booleanSetting(settingId: String): Boolean = config.getBoolean(record.addonId, settingId)

	override fun onKeybind(keybindId: String, handler: () -> Unit): RegistrationHandle =
		track(platform.bindKeybindHandler(platformKeybindId(record.id, keybindId), handler))

	override fun addHudText(widgetId: String, provider: () -> String?): RegistrationHandle =
		track(platform.addHudWidget(platformWidgetId(record.id, widgetId), provider))

	override fun sendChat(message: String) = platform.sendChat(message)

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

	override fun sendChat(message: String) = platform.sendChat(message)
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
