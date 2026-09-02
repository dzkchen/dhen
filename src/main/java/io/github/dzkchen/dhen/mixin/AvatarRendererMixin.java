package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.NametagTweaks;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("RETURN")
	)
	private void dhen$forceNameTagVisibility(
		final Avatar entity,
		final AvatarRenderState state,
		final float partialTicks,
		final CallbackInfo callback
	) {
		if (entity instanceof Player && NametagTweaks.forcesNametags()) {
			state.isDiscrete = false;
		}
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void dhen$showOwnNameTag(
		final Avatar entity,
		final double distanceToCameraSq,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (NametagTweaks.showsOwnNametag(entity)) {
			callback.setReturnValue(true);
		}
	}
}
