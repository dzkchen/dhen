package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.event.RenderHooks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Unique
	private boolean dhen$barHidden;

	@WrapWithCondition(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V"
		)
	)
	private boolean dhen$beforeBar(
		final BossHealthOverlay overlay,
		final GuiGraphicsExtractor extractor,
		final int x,
		final int y,
		final BossEvent bossBar
	) {
		this.dhen$barHidden = RenderHooks.bossBarCancelled(bossBar);
		return !this.dhen$barHidden;
	}

	@WrapWithCondition(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
		)
	)
	private boolean dhen$beforeBarName(
		final GuiGraphicsExtractor extractor,
		final Font font,
		final Component name,
		final int x,
		final int y,
		final int color
	) {
		final boolean hidden = this.dhen$barHidden;
		this.dhen$barHidden = false;
		return !hidden;
	}
}
