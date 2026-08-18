package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.ScreenHooks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeClick(
		final MouseButtonEvent click,
		final boolean doubleClick,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (ScreenHooks.beforeContainerClick((AbstractContainerScreen<?>)(Object)this, click, this.hoveredSlot)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void dhen$beforeKey(final KeyEvent key, final CallbackInfoReturnable<Boolean> callback) {
		if (ScreenHooks.beforeContainerKey((AbstractContainerScreen<?>)(Object)this, key, this.hoveredSlot)) {
			callback.setReturnValue(true);
		}
	}
}
