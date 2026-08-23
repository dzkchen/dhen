package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.NetworkHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(
		method = "handleBundlePacket",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void dhen$handleBundle(final ClientboundBundlePacket packet, final CallbackInfo callback) {
		if (!NetworkHooks.INSTANCE.active()) {
			return;
		}
		final ClientPacketListener listener = (ClientPacketListener)(Object)this;
		for (final Packet<? super ClientGamePacketListener> subPacket : packet.subPackets()) {
			if (!NetworkHooks.beforeHandle(subPacket)) {
				subPacket.handle(listener);
				NetworkHooks.afterHandle(subPacket);
			}
		}
		callback.cancel();
	}
}
