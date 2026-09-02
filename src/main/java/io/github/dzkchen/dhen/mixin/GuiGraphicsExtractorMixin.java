package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.dzkchen.dhen.features.inventory.ItemTooltip;
import io.github.dzkchen.dhen.features.qol.Tweaks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

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

	@WrapMethod(method = "tooltip")
	private void dhen$scrollTooltip(
		final Font font,
		final List<ClientTooltipComponent> lines,
		final int x,
		final int y,
		final ClientTooltipPositioner positioner,
		final @Nullable Identifier style,
		final Operation<Void> original
	) {
		if (!ItemTooltip.scrolling()) {
			original.call(font, lines, x, y, positioner, style);
			return;
		}
		final GuiGraphicsExtractor graphics = (GuiGraphicsExtractor)(Object)this;
		graphics.pose().pushMatrix();
		try {
			ItemTooltip.transformTooltip(graphics, x, y);
			original.call(font, lines, x, y, positioner, style);
		} finally {
			graphics.pose().popMatrix();
		}
	}
}
