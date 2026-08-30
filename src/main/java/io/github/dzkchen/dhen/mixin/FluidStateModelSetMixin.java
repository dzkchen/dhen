package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.VisualTweaks;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidStateModelSet.class)
public abstract class FluidStateModelSetMixin {
	@Inject(method = "get", at = @At("RETURN"), cancellable = true)
	private void dhen$fluidModel(final FluidState state, final CallbackInfoReturnable<FluidModel> callback) {
		final FluidModel original = callback.getReturnValue();
		final FluidModel replacement = VisualTweaks.replaceModel((FluidStateModelSet)(Object)this, state, original);
		if (replacement != original) {
			callback.setReturnValue(replacement);
		}
	}
}
