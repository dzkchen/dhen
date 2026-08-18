package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.ScreenHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeRender(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		if (ScreenHooks.beforeScreenRender((Screen)(Object)this, graphics, mouseX, mouseY)) {
			callback.cancel();
		}
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
	private void dhen$afterRender(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		ScreenHooks.afterScreenRender((Screen)(Object)this, graphics, mouseX, mouseY);
	}
}
