package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.qol.Tweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@WrapWithCondition(
		method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemCooldown(Lnet/minecraft/world/item/ItemStack;II)V"
		)
	)
	private boolean dhen$showItemCooldown(
		final GuiGraphicsExtractor graphics,
		final ItemStack stack,
		final int x,
		final int y
	) {
		return !Tweaks.shouldHideItemCooldown();
	}
}
