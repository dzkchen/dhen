package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.dzkchen.dhen.features.visual.RevertAxes;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@ModifyExpressionValue(
		method = "appendItemLayers",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
		)
	)
	private Identifier dhen$revertAxeModel(
		final Identifier model,
		@Local(argsOnly = true) final ItemStack stack
	) {
		return RevertAxes.model(stack, model);
	}
}
