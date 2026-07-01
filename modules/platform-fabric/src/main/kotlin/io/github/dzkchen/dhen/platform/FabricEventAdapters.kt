package io.github.dzkchen.dhen.platform

import io.github.dzkchen.dhen.api.ChatReceiveEvent
import io.github.dzkchen.dhen.api.ClientTickEvent
import io.github.dzkchen.dhen.api.WorldJoinEvent
import io.github.dzkchen.dhen.api.WorldLeaveEvent
import io.github.dzkchen.dhen.runtime.DhenRuntime
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

object FabricEventAdapters {
	fun register(runtime: DhenRuntime) {
		ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { runtime.dispatch(ClientTickEvent) })
		ClientPlayConnectionEvents.JOIN.register(
			ClientPlayConnectionEvents.Join { _, _, _ -> runtime.dispatch(WorldJoinEvent) },
		)
		ClientPlayConnectionEvents.DISCONNECT.register(
			ClientPlayConnectionEvents.Disconnect { _, _ -> runtime.dispatch(WorldLeaveEvent) },
		)
		ClientReceiveMessageEvents.GAME.register(
			ClientReceiveMessageEvents.Game { message, overlay -> runtime.dispatch(ChatReceiveEvent(message.string, overlay)) },
		)
		ClientReceiveMessageEvents.CHAT.register(
			ClientReceiveMessageEvents.Chat { message, _, _, _, _ -> runtime.dispatch(ChatReceiveEvent(message.string, false)) },
		)
	}
}
