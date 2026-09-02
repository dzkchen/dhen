package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.InputHooks;
import io.github.dzkchen.dhen.event.ScreenHooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeKeyPress(
		final long window,
		final int action,
		final KeyEvent key,
		final CallbackInfo callback
	) {
		if (window != this.minecraft.getWindow().handle()) {
			return;
		}
		if (InputHooks.beforeKey(key.key(), key.scancode(), key.modifiers(), action)) {
			callback.cancel();
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeCharTyped(final long window, final CharacterEvent character, final CallbackInfo callback) {
		if (window != this.minecraft.getWindow().handle()) {
			return;
		}
		final Screen screen = this.minecraft.gui.screen();
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}
		if (ScreenHooks.beforeContainerChar(container, character.codepoint())) {
			callback.cancel();
		}
	}
}
