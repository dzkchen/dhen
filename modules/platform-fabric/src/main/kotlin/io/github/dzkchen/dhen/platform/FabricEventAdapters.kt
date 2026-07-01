package io.github.dzkchen.dhen.platform

import io.github.dzkchen.dhen.api.ChatReceiveEvent
import io.github.dzkchen.dhen.api.ClientTickEvent
import io.github.dzkchen.dhen.api.ServerJoinEvent
import io.github.dzkchen.dhen.api.ServerLeaveEvent
import io.github.dzkchen.dhen.runtime.DhenRuntime
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

object FabricEventAdapters {
	fun register(runtime: DhenRuntime) {
		ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { runtime.dispatch(ClientTickEvent) })
		ClientPlayConnectionEvents.JOIN.register(
			ClientPlayConnectionEvents.Join { _, _, _ -> Minecraft.getInstance().execute { runtime.dispatch(ServerJoinEvent) } },
		)
		ClientPlayConnectionEvents.DISCONNECT.register(
			ClientPlayConnectionEvents.Disconnect { _, _ -> Minecraft.getInstance().execute { runtime.dispatch(ServerLeaveEvent) } },
		)
		ClientReceiveMessageEvents.GAME.register(
			ClientReceiveMessageEvents.Game { message, overlay -> runtime.dispatch(ChatReceiveEvent(message.string, overlay)) },
		)
		ClientReceiveMessageEvents.CHAT.register(
			ClientReceiveMessageEvents.Chat { message, _, _, _, _ -> runtime.dispatch(ChatReceiveEvent(message.string, false)) },
		)
	}
}
