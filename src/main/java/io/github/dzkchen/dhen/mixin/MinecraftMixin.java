package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.visual.RenderOptimizer;
import io.github.dzkchen.dhen.privacy.TelemetryBlocking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
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

	@WrapWithCondition(
		method = "runTick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/texture/TextureManager;tick()V"
		)
	)
	private boolean dhen$animateTextures(final TextureManager textures) {
		return !RenderOptimizer.freezesTextureAnimation();
	}

	@ModifyReturnValue(method = "isGameLoadFinished", at = @At("RETURN"))
	private boolean dhen$skipWorldWhileUnfocused(final boolean finished) {
		return finished && !RenderOptimizer.skipsUnfocusedWorld();
	}

	@ModifyExpressionValue(
		method = "renderFrame",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;getFramerateLimit()I"
		)
	)
	private int dhen$throttleWhileUnfocused(final int limit) {
		final int unfocused = RenderOptimizer.unfocusedFrameCap();
		if (unfocused <= 0) return limit;
		return limit > 0 ? Math.min(unfocused, limit) : unfocused;
	}
}
