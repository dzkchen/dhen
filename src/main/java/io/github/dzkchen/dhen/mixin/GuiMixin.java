package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.ScreenHooks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@Shadow
	private Screen screen;

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void dhen$screenChanged(final Screen opening, final CallbackInfo callback) {
		ScreenHooks.screenChanged(this.screen, opening);
	}
}
