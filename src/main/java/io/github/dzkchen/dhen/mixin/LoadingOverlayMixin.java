package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.gui.LoadingSplash;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	@Final
	private ReloadInstance reload;

	@Shadow
	@Final
	private boolean fadeIn;

	@Shadow
	private long fadeInStart;

	@Shadow
	private long fadeOutStart;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void dhen$paintSplash(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callback
	) {
		if (!LoadingSplash.enabled()) {
			return;
		}
		final long now = Util.getMillis();
		if (this.fadeIn && this.fadeInStart == -1L) {
			this.fadeInStart = now;
		}
		if (LoadingSplash.paint(graphics, this.reload, this.fadeOutStart, this.fadeInStart, this.fadeIn, mouseX, mouseY, now, partialTick)) {
			callback.cancel();
		}
	}
}
