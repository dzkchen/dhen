package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.render.ItemModels;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
	private boolean dhen$forceFoil(final boolean foil) {
		return ItemModels.foil((ItemStack)(Object)this, foil);
	}
}
