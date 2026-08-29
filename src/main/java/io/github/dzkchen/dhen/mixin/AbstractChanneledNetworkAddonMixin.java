package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.privacy.ChannelSpoofing;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.fabricmc.fabric.impl.networking.client.ClientConfigurationNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractChanneledNetworkAddon.class)
public abstract class AbstractChanneledNetworkAddonMixin {
	@Inject(method = "createRegistrationPayload", at = @At("HEAD"), cancellable = true)
	private void dhen$announceOnlyAllowedChannels(
		final CustomPacketPayload.Type<RegistrationPayload> type,
		final Collection<Identifier> channels,
		final CallbackInfoReturnable<RegistrationPayload> info
	) {
		if (!dhen$isClientAddon()) {
			return;
		}
		final List<Identifier> retained = ChannelSpoofing.retained(channels);
		if (retained != null) {
			info.setReturnValue(retained.isEmpty() ? null : new RegistrationPayload(type, retained));
		}
	}

	@Inject(method = "handle", at = @At("HEAD"), cancellable = true)
	private void dhen$dropBlockedInboundChannel(
		final CustomPacketPayload payload,
		final CallbackInfoReturnable<Boolean> info
	) {
		if (dhen$isClientAddon() && ChannelSpoofing.blocks(payload.type().id())) {
			info.setReturnValue(false);
		}
	}

	@Unique
	private boolean dhen$isClientAddon() {
		return (Object) this instanceof ClientPlayNetworkAddon
			|| (Object) this instanceof ClientConfigurationNetworkAddon;
	}
}
