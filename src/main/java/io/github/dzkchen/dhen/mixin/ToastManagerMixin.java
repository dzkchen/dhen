package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.qol.Tweaks;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void dhen$hideToast(final Toast toast, final CallbackInfo callback) {
		if (Tweaks.hidesToast(toast)) {
			callback.cancel();
		}
	}
}
