package io.github.dzkchen.dhen.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dzkchen.dhen.event.InputHooks;
import io.github.dzkchen.dhen.features.qol.NoCursorReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	@Unique
	private double dhen$keptX;

	@Unique
	private double dhen$keptY;

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

	@Inject(
		method = "grabMouse",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/MouseHandler;xpos:D",
			opcode = Opcodes.PUTFIELD,
			ordinal = 0
		)
	)
	private void dhen$rememberCursor(final CallbackInfo callback) {
		this.dhen$keptX = this.xpos;
		this.dhen$keptY = this.ypos;
		NoCursorReset.cursorGrabbed();
	}

	@Inject(
		method = "releaseMouse",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V",
			shift = At.Shift.AFTER
		)
	)
	private void dhen$keepCursor(final CallbackInfo callback) {
		if (!NoCursorReset.keepsCursorPosition()) {
			return;
		}
		this.xpos = this.dhen$keptX;
		this.ypos = this.dhen$keptY;
		InputConstants.grabOrReleaseMouse(
			this.minecraft.getWindow(),
			InputConstants.CURSOR_NORMAL,
			this.dhen$keptX,
			this.dhen$keptY
		);
	}
}
