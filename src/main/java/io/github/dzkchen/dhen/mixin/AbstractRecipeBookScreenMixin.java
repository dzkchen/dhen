package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.qol.Tweaks;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@Inject(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;initButton()V"
		),
		cancellable = true
	)
	private void dhen$hideRecipeBook(final CallbackInfo callback) {
		if (Tweaks.shouldHideRecipeBook()) {
			callback.cancel();
		}
	}
}
