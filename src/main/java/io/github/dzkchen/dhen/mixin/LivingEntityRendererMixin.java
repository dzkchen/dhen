package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dzkchen.dhen.features.visual.NametagTweaks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static final String SHOULD_SHOW_NAME = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z";

	@Inject(method = SHOULD_SHOW_NAME, at = @At("HEAD"), cancellable = true)
	private void dhen$hideDinnerboneNameTag(
		final LivingEntity entity,
		final double distanceToCameraSq,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (NametagTweaks.hidesDinnerboneNametag(entity)) {
			callback.setReturnValue(false);
		}
	}

	@ModifyExpressionValue(
		method = SHOULD_SHOW_NAME,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDiscrete()Z")
	)
	private boolean dhen$forceSneakingNameTag(final boolean discrete, final LivingEntity entity) {
		return discrete && !(entity instanceof Player && NametagTweaks.forcesNametags());
	}

	@ModifyExpressionValue(
		method = SHOULD_SHOW_NAME,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z")
	)
	private boolean dhen$forceInvisibleNameTag(final boolean invisible, final LivingEntity entity) {
		final boolean forced = entity instanceof Player
			&& entity.getUUID().version() == 4
			&& NametagTweaks.forcesNametags();
		return invisible && !forced;
	}
}
