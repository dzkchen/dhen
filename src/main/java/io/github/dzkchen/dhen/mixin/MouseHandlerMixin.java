package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.InputHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeButton(
		final long window,
		final MouseButtonInfo button,
		final int action,
		final CallbackInfo callback
	) {
		if (window != this.minecraft.getWindow().handle()) {
			return;
		}
		if (InputHooks.beforeMouseButton(button.button(), button.modifiers(), action)) {
			callback.cancel();
		}
	}
}
