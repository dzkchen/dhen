package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.visual.RenderOptimizer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@WrapWithCondition(
		method = "extract",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;extractRenderState"
				+ "(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;"
				+ "Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V"
		)
	)
	private boolean dhen$extractWeather(
		final WeatherEffectRenderer renderer,
		final ClientLevel level,
		final float partialTicks,
		final Vec3 cameraPos,
		final WeatherRenderState state
	) {
		return !RenderOptimizer.hidesWeather();
	}
}
