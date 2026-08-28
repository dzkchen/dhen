package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.input.TextInputTarget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements TextInputTarget {
	@Shadow
	private EditBox searchBox;

	@Shadow
	public abstract boolean isVisible();

	@Override
	public boolean getTextInputFocused() {
		return this.isVisible() && this.searchBox != null && this.searchBox.isFocused() && this.searchBox.isVisible();
	}
}
