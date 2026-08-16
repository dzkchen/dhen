package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.NetworkHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorMixin {
	@Shadow
	@Final
	private Packet<?> packet;

	@Shadow
	@Final
	private PacketListener listener;

	@Inject(
		method = "handle",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),
		cancellable = true
	)
	private void dhen$beforeHandle(final CallbackInfo callback) {
		if (this.listener instanceof ClientPacketListener && NetworkHooks.beforeHandle(this.packet)) {
			callback.cancel();
		}
	}

	@Inject(
		method = "handle",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V",
			shift = At.Shift.AFTER
		)
	)
	private void dhen$afterHandle(final CallbackInfo callback) {
		if (this.listener instanceof ClientPacketListener) {
			NetworkHooks.afterHandle(this.packet);
		}
	}
}
