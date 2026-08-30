package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.qol.Tweaks;
import io.github.dzkchen.dhen.features.visual.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void dhen$hideHotbarTooltip(final CallbackInfo callback) {
		if (Tweaks.shouldHideHotbarTooltip()) {
			callback.cancel();
		}
	}

	@WrapWithCondition(
		method = "extractCameraOverlays",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;extractPortalOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"
		)
	)
	private boolean dhen$showPortal(final Hud hud, final GuiGraphicsExtractor graphics, final float alpha) {
		return !Camera.shouldHidePortalOverlay();
	}

	@ModifyExpressionValue(
		method = "extractCameraOverlays",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"
		)
	)
	private float dhen$nausea(final float original) {
		return Camera.nauseaIntensity(original);
	}
}
