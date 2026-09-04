package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.privacy.PackControls;
import net.minecraft.server.packs.repository.PackCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PackCompatibility.class)
public abstract class PackCompatibilityMixin {
	@ModifyReturnValue(method = "isCompatible", at = @At("RETURN"))
	private boolean dhen$ignorePackVersion(final boolean compatible) {
		return compatible || PackControls.ignoresCompatibility();
	}
}
