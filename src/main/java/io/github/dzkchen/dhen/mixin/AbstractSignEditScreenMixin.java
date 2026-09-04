package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.inventory.SignCalculator;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {
	@Shadow
	@Final
	private String[] messages;

	@Shadow
	public abstract void onClose();

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void dhen$enterConfirms(final KeyEvent key, final CallbackInfoReturnable<Boolean> callback) {
		if (key.isConfirmation() && !key.hasShiftDown() && SignCalculator.confirmsOnEnter(this.messages)) {
			this.onClose();
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "onDone", at = @At("HEAD"))
	private void dhen$applyCalculation(final CallbackInfo callback) {
		SignCalculator.applyTo(this.messages);
	}
}
