package io.github.dzkchen.dhen.runtime

import io.github.dzkchen.dhen.api.AddonLogger
import io.github.dzkchen.dhen.api.ChatContext
import io.github.dzkchen.dhen.api.ClientContext
import io.github.dzkchen.dhen.api.DhenEvent
import io.github.dzkchen.dhen.api.HudRenderContext
import io.github.dzkchen.dhen.api.KeybindId
import io.github.dzkchen.dhen.api.ModuleDisableContext
import io.github.dzkchen.dhen.api.ModuleEnableContext
import io.github.dzkchen.dhen.api.ModuleId
import io.github.dzkchen.dhen.api.RegistrationHandle
import io.github.dzkchen.dhen.api.SettingId
import io.github.dzkchen.dhen.api.WidgetId
import kotlin.reflect.KClass

internal fun platformKeybindId(moduleId: ModuleId, keybindId: KeybindId): String = "${moduleId.value}/${keybindId.value}"

internal fun platformKeybindId(moduleId: ModuleId, keybindId: String): String = platformKeybindId(moduleId, KeybindId(keybindId))

internal fun platformWidgetId(moduleId: ModuleId, widgetId: WidgetId): String = "${moduleId.value}/${widgetId.value}"

private class EventSubscriptionHandle(
	override val id: String,
	private val subscription: EventBus.Subscription,
) : RegistrationHandle {
	override fun dispose() = subscription.cancel()
}

private class EnableContextImpl(
	private val record: ModuleRecord,
	private val platform: PlatformServices,
	private val config: ConfigManager,
	private val eventBus: EventBus,
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

	override fun <T : DhenEvent> onEvent(type: KClass<T>, handler: (T) -> Unit): RegistrationHandle {
		val subscription = eventBus.subscribe(type, handler)
		return track(EventSubscriptionHandle("event:${record.id.value}:${type.simpleName}", subscription))
	}

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

class LifecycleManager(
	private val platform: PlatformServices,
	private val config: ConfigManager,
	private val eventBus: EventBus,
	private val logger: AddonLogger,
) {
	fun enable(record: ModuleRecord) {
		if (!record.state.canTransitionTo(LifecycleState.ENABLED)) return
		val context = EnableContextImpl(record, platform, config, eventBus, logger)
		try {
			record.module.onEnable(context)
			record.transitionTo(LifecycleState.ENABLED, "enabled")
		} catch (t: Throwable) {
			logger.error("Failed to enable module ${record.id}", t)
			disposeHandles(record)
			record.transitionTo(LifecycleState.FAILED, "enable-failed", t.message ?: t.javaClass.simpleName)
		}
	}

	fun disable(record: ModuleRecord) {
		if (!record.state.canTransitionTo(LifecycleState.DISABLED)) return
		if (record.state == LifecycleState.ENABLED) {
			try {
				record.module.onDisable(DisableContextImpl(record, platform, logger))
			} catch (t: Throwable) {
				logger.error("onDisable threw for module ${record.id}; continuing cleanup", t)
			}
		}
		disposeHandles(record)
		record.transitionTo(LifecycleState.DISABLED, "disabled")
	}

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
