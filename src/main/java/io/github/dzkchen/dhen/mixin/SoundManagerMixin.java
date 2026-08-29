package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.qol.ArrowHitSound;
import io.github.dzkchen.dhen.sound.SoundManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.sounds.SoundManager.class)
public abstract class SoundManagerMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void dhen$replaceArrowHit(
		final SoundInstance sound,
		final CallbackInfoReturnable<SoundEngine.PlayResult> callback
	) {
		SoundManager.recordPlayedSound(sound);
		if (ArrowHitSound.onSoundPlay(sound)) {
			callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}
}
