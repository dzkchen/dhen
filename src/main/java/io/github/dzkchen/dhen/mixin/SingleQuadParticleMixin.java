package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dzkchen.dhen.render.ParticleAlpha;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin implements ParticleAlpha {
	@Shadow
	protected float alpha;

	@Shadow
	protected abstract void setAlpha(float alpha);

	@Unique
	private boolean dhen$faded;

	@Unique
	private float dhen$alphaCap = 1.0F;

	@Override
	public float dhenParticleAlpha() {
		return this.alpha;
	}

	@Override
	public void dhenFadeParticle(final float alpha) {
		this.dhen$alphaCap = alpha;
		this.dhen$faded = true;
		this.setAlpha(alpha);
	}

	@ModifyVariable(method = "setAlpha", at = @At("HEAD"), argsOnly = true)
	private float dhen$capAlpha(final float alpha) {
		return this.dhen$faded && alpha > this.dhen$alphaCap ? this.dhen$alphaCap : alpha;
	}

	@ModifyExpressionValue(
		method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;"
			+ "Lorg/joml/Quaternionf;FFFF)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/particle/SingleQuadParticle;getLayer()"
				+ "Lnet/minecraft/client/particle/SingleQuadParticle$Layer;"
		)
	)
	private SingleQuadParticle.Layer dhen$blendFadedParticle(final SingleQuadParticle.Layer layer) {
		if (this.dhen$faded && layer == SingleQuadParticle.Layer.OPAQUE) {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
		return layer;
	}
}
