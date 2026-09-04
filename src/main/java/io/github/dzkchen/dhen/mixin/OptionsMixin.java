package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.features.visual.Camera;
import io.github.dzkchen.dhen.features.visual.RenderOptimizer;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Options.class)
public abstract class OptionsMixin {
	@ModifyVariable(method = "setCameraType", at = @At("HEAD"), argsOnly = true)
	private CameraType dhen$frontCamera(final CameraType type) {
		return Camera.replaceCameraType(type);
	}

	@ModifyReturnValue(method = "getCloudStatus", at = @At("RETURN"))
	private CloudStatus dhen$hideIslandClouds(final CloudStatus status) {
		return RenderOptimizer.hidesClouds() ? CloudStatus.OFF : status;
	}
}
