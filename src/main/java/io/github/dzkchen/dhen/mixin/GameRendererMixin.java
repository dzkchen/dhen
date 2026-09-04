package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dzkchen.dhen.features.qol.Tweaks;
import io.github.dzkchen.dhen.features.visual.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@ModifyExpressionValue(
		method = {"tick", "renderLevel"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"
		)
	)
	private float dhen$nausea(final float original) {
		return Camera.nauseaIntensity(original);
	}

	@ModifyExpressionValue(
		method = "nightVisionScale",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffectInstance;endsWithin(I)Z"
		)
	)
	private static boolean dhen$steadyNightVision(final boolean original) {
		return original && !Tweaks.steadiesNightVision();
	}
}
