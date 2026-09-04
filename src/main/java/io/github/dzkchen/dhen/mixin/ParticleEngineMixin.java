package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dzkchen.dhen.render.ParticleOverrides;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@ModifyExpressionValue(
		method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)"
			+ "Lnet/minecraft/client/particle/Particle;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/particle/ParticleEngine;makeParticle"
				+ "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"
		)
	)
	private Particle dhen$tuneParticle(
		final Particle particle,
		final ParticleOptions options,
		final double x,
		final double y,
		final double z,
		final double xa,
		final double ya,
		final double za
	) {
		if (particle == null) return null;
		return ParticleOverrides.tuned(particle, options.getType());
	}
}
