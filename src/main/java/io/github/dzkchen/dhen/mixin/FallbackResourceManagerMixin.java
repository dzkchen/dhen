package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.PackOverrides;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
	@Shadow
	public abstract List<Resource> getResourceStack(Identifier location);

	@Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
	private void dhen$selectTextShaders(
		final String path,
		final Predicate<Identifier> filter,
		final CallbackInfoReturnable<Map<Identifier, Resource>> callback
	) {
		final Map<Identifier, Resource> selected = PackOverrides.selectTextShaders(
			callback.getReturnValue(),
			this::getResourceStack
		);
		if (selected != null) {
			callback.setReturnValue(selected);
		}
	}
}
