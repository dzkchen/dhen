package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dzkchen.dhen.features.visual.RenderOptimizer;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@ModifyExpressionValue(
		method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Options;getFinalSoundSourceVolume"
				+ "(Lnet/minecraft/sounds/SoundSource;)F"
		)
	)
	private float dhen$muteWhileUnfocused(final float volume) {
		return RenderOptimizer.mutesUnfocused() ? 0.0F : volume;
	}
}
