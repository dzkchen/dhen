package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.dzkchen.dhen.render.ItemModels;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerHeadSpecialRenderer.class)
public abstract class PlayerHeadSpecialRendererMixin {
	@ModifyExpressionValue(
		method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
		)
	)
	private Object dhen$skyblockHeadProfile(
		final Object profile,
		@Local(argsOnly = true) final ItemStack stack
	) {
		return ItemModels.headProfile(stack, (ResolvableProfile) profile);
	}
}
