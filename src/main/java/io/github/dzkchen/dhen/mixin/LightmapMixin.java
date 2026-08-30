package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.Camera;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Lightmap.class)
public abstract class LightmapMixin {
	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
	private LightmapRenderState dhen$fullBright(final LightmapRenderState state) {
		return Camera.applyFullBright(state);
	}
}
