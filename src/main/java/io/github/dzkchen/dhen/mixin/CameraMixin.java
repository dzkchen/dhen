package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.features.visual.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float fov;

	@ModifyExpressionValue(
		method = "alignWithEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"
		)
	)
	private double dhen$cameraDistance(final double original) {
		return Camera.cameraDistance(original);
	}

	@ModifyReturnValue(method = "getMaxZoom", at = @At("RETURN"))
	private float dhen$cameraClip(final float original, final float requested) {
		return Camera.maxZoom(original, requested);
	}

	@ModifyExpressionValue(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F")
	)
	private float dhen$eyeHeight(final float original) {
		return Camera.eyeHeight(original);
	}

	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float dhen$fov(final float original) {
		return Camera.fov(original);
	}

	@ModifyExpressionValue(
		method = "createProjectionMatrixForCulling",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F")
	)
	private float dhen$cullingFov(final float original) {
		return Camera.cullingFov(original, this.fov);
	}

	@ModifyExpressionValue(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z",
			ordinal = 0
		)
	)
	private boolean dhen$blindnessBlocksSky(final boolean original) {
		return original && !Camera.shouldDisableBlindness();
	}
}
