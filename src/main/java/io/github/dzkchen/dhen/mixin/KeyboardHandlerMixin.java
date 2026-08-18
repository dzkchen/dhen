package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.InputHooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
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
}
