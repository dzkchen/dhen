package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.NametagTweaks;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagFeatureRendererMixin {
	private static final String PREPARE_TEXT =
		"Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;";

	@ModifyArg(
		method = "prepareText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;)Lnet/minecraft/client/gui/Font$PreparedText;",
		at = @At(value = "INVOKE", target = PREPARE_TEXT),
		index = 4
	)
	private static boolean dhen$shadowNameTag(final boolean dropShadow) {
		return dropShadow || NametagTweaks.shadowsNametagText();
	}

	@ModifyArg(
		method = "prepareText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;)Lnet/minecraft/client/gui/Font$PreparedText;",
		at = @At(value = "INVOKE", target = PREPARE_TEXT),
		index = 6
	)
	private static int dhen$nameTagBackground(final int backgroundColor) {
		return NametagTweaks.hidesNametagBackground() ? 0 : backgroundColor;
	}
}
