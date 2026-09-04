package io.github.dzkchen.dhen.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.dzkchen.dhen.features.visual.RenderOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
	@Inject(method = "onFocus", at = @At("TAIL"))
	private void dhen$refreshVolumeOnFocusChange(
		final long handle,
		final boolean focused,
		final CallbackInfo callback
	) {
		RenderOptimizer.refreshVolumeOnFocusChange();
	}
}
