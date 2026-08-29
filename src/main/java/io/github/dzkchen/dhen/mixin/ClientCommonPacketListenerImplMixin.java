package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.ServerPacks;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	@Inject(
		method = "handleResourcePackPush",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread"
				+ "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
				+ "Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void dhen$serverPackPushed(
		final ClientboundResourcePackPushPacket packet,
		final CallbackInfo callback
	) {
		ServerPacks.pushed(packet.id());
	}

	@Inject(
		method = "handleResourcePackPop",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread"
				+ "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
				+ "Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void dhen$serverPackPopped(
		final ClientboundResourcePackPopPacket packet,
		final CallbackInfo callback
	) {
		ServerPacks.popped(packet.id().orElse(null));
	}
}
