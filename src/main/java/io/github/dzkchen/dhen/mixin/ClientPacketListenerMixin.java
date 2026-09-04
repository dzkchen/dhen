package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dzkchen.dhen.event.NetworkHooks;
import io.github.dzkchen.dhen.features.chat.ChatTweaks;
import io.github.dzkchen.dhen.privacy.PacketContext;
import io.github.dzkchen.dhen.privacy.TranslationProtection;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.objectweb.asm.Opcodes;
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
		final ClientPacketListener listener = (ClientPacketListener)(Object)this;
		for (final Packet<? super ClientGamePacketListener> subPacket : packet.subPackets()) {
			if (!NetworkHooks.beforeHandle(subPacket)) {
				TranslationProtection.clearDedup();
				PacketContext.beginHandle(subPacket);
				try {
					subPacket.handle(listener);
				} finally {
					PacketContext.endHandle();
					NetworkHooks.afterHandle(subPacket);
				}
			}
		}
		callback.cancel();
	}

	@ModifyExpressionValue(
		method = "handleLogin",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;seenInsecureChatWarning:Z",
			opcode = Opcodes.GETFIELD
		)
	)
	private boolean dhen$hideSigningWarning(final boolean original) {
		return original || ChatTweaks.hidesSigningWarning();
	}
}
