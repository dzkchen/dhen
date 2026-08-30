package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.dzkchen.dhen.event.ScreenHooks;
import io.github.dzkchen.dhen.features.visual.VisualTweaks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@Shadow
	private Screen screen;

	@Shadow
	private Overlay overlay;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen dhen$screenChanged(final Screen opening) {
		return ScreenHooks.screenChanged(this.screen, opening);
	}

	@ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
	private Screen dhen$screenSynthesised(final Screen synthesised) {
		return ScreenHooks.screenSynthesised(synthesised);
	}

	@Inject(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;extractDeferredSubtitles()V",
			shift = At.Shift.AFTER
		)
	)
	private void dhen$darkModeOverDeferredHud(
		final DeltaTracker deltaTracker,
		final boolean shouldRenderLevel,
		final boolean resourcesLoaded,
		final CallbackInfo callback,
		@Local final GuiGraphicsExtractor graphics
	) {
		VisualTweaks.drawOverDeferredHud(graphics, shouldRenderLevel && this.overlay == null);
	}
}
