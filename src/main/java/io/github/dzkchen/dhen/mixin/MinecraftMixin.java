package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.TelemetryBlocking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
	private void dhen$refuseTelemetry(final CallbackInfoReturnable<Boolean> info) {
		TelemetryBlocking.refused();
		info.setReturnValue(false);
	}
}
