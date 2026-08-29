package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.qol.Tweaks;
import io.github.dzkchen.dhen.input.TextInputTarget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements TextInputTarget {
	@Shadow
	private EditBox searchBox;

	@Shadow
	public abstract boolean isVisible();

	@Shadow
	protected abstract void setVisible(boolean visible);

	@Inject(method = "init", at = @At("TAIL"))
	private void dhen$closeRecipeBook(final CallbackInfo callback) {
		if (Tweaks.shouldCloseRecipeBook()) {
			this.setVisible(false);
		}
	}

	@Override
	public boolean getTextInputFocused() {
		return this.isVisible() && this.searchBox != null && this.searchBox.isFocused() && this.searchBox.isVisible();
	}
}
