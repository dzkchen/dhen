package io.github.dzkchen.dhen.mixin;

import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import io.github.dzkchen.dhen.privacy.TelemetryBlocking;
import java.util.concurrent.Executor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = YggdrasilUserApiService.class, remap = false)
public abstract class YggdrasilUserApiServiceMixin {
	@Inject(method = "newTelemetrySession", at = @At("HEAD"), cancellable = true)
	private void dhen$refuseTelemetrySession(
		final Executor executor,
		final CallbackInfoReturnable<TelemetrySession> info
	) {
		TelemetryBlocking.refused();
		info.setReturnValue(TelemetrySession.DISABLED);
	}
}
