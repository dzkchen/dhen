package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.qol.Tweaks;
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
}
