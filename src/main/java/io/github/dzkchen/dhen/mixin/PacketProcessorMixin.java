package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.dzkchen.dhen.event.NetworkHooks;
import io.github.dzkchen.dhen.privacy.PacketContext;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public class PacketProcessorMixin {
	@WrapOperation(
		method = "handle",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
		)
	)
	private void dhen$handle(
		final Packet<?> packet,
		final PacketListener listener,
		final Operation<Void> original
	) {
		if (!(listener instanceof ClientPacketListener)) {
			original.call(packet, listener);
			return;
		}
		if (NetworkHooks.beforeHandle(packet)) {
			return;
		}
		PacketContext.beginHandle(packet);
		try {
			original.call(packet, listener);
		} finally {
			PacketContext.endHandle();
			NetworkHooks.afterHandle(packet);
		}
	}
}
