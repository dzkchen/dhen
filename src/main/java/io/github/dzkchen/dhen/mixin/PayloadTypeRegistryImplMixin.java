package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.privacy.ChannelSpoofing;
import io.github.dzkchen.dhen.privacy.PacketContext;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PayloadTypeRegistryImpl.class)
public class PayloadTypeRegistryImplMixin {
	@Inject(
		method = "get(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void dhen$hideBlockedInboundCodec(
		final Identifier id,
		final CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> info
	) {
		if (!PacketContext.isDecodingPayload()) {
			return;
		}
		final Object registry = this;
		if (registry != PayloadTypeRegistryImpl.CLIENTBOUND_PLAY
			&& registry != PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION) {
			return;
		}
		if (ChannelSpoofing.blocks(id)) {
			info.setReturnValue(null);
		}
	}
}
