package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.Camera;
import net.minecraft.client.CameraType;
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
}
