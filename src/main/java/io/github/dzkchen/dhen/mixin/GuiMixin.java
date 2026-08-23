package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.ScreenHooks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@Shadow
	private Screen screen;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen dhen$screenChanged(final Screen opening) {
		return ScreenHooks.screenChanged(this.screen, opening);
	}
}
