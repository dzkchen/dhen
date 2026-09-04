package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.visual.RenderOptimizer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@WrapWithCondition(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;tickWeatherEffects()V"
		)
	)
	private boolean dhen$tickWeather(final ClientLevel level) {
		return !RenderOptimizer.hidesWeather();
	}
}
