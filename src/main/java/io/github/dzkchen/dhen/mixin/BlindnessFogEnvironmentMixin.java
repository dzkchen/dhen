package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.features.visual.Camera;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlindnessFogEnvironment.class)
public abstract class BlindnessFogEnvironmentMixin {
	@ModifyReturnValue(method = "getMobEffect", at = @At("RETURN"))
	private Holder<MobEffect> dhen$blindness(final Holder<MobEffect> original) {
		return Camera.shouldDisableBlindness() ? null : original;
	}
}
