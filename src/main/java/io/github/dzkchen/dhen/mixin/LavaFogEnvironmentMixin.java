package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.LavaToWater;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogEnvironmentMixin {
	@Unique
	private static final WaterFogEnvironment DHEN_WATER_FOG = new WaterFogEnvironment();

	@Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
	private void dhen$lavaFog(
		final FogData fog,
		final Camera camera,
		final ClientLevel level,
		final float renderDistance,
		final DeltaTracker deltaTracker,
		final CallbackInfo callback
	) {
		if (LavaToWater.shouldHideFog()) {
			LavaToWater.hideFog(fog, renderDistance);
			callback.cancel();
		}
	}

	@Inject(method = "getBaseColor", at = @At("HEAD"), cancellable = true)
	private void dhen$lavaColor(
		final ClientLevel level,
		final Camera camera,
		final int renderDistance,
		final float partialTicks,
		final CallbackInfoReturnable<Integer> callback
	) {
		if (LavaToWater.isActive()) {
			final int water = DHEN_WATER_FOG.getBaseColor(level, camera, renderDistance, partialTicks);
			callback.setReturnValue(LavaToWater.fogColor(water));
		}
	}
}
