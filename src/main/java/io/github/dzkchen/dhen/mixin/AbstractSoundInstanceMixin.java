package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.sound.SoundManager;
import io.github.dzkchen.dhen.sound.SubstituteSound;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
	@ModifyReturnValue(method = "getVolume", at = @At("RETURN"))
	private float dhen$applyRuleVolume(final float original) {
		final AbstractSoundInstance sound = (AbstractSoundInstance)(Object)this;
		if (sound instanceof SubstituteSound) {
			return original;
		}
		return original * SoundManager.volumeOf(sound.getIdentifier());
	}
}
