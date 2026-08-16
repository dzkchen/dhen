package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.NetworkHooks;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeSend(
		final Packet<?> packet,
		final ChannelFutureListener listener,
		final boolean flush,
		final CallbackInfo callback
	) {
		if (Minecraft.getInstance().isSameThread() && NetworkHooks.beforeSend(packet)) {
			callback.cancel();
		}
	}
}
