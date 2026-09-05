package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.inventory.BetterContainers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
	@WrapWithCondition(
		method = "extractBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
		)
	)
	private boolean dhen$showChestTexture(
		final GuiGraphicsExtractor graphics,
		final RenderPipeline pipeline,
		final Identifier texture,
		final int x,
		final int y,
		final float u,
		final float v,
		final int width,
		final int height,
		final int textureWidth,
		final int textureHeight
	) {
		return !BetterContainers.overriding();
	}
}
