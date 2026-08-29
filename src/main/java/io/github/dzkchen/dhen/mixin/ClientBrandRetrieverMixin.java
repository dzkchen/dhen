package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.privacy.SpoofAsVanilla;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public abstract class ClientBrandRetrieverMixin {
	@Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
	private static void dhen$reportVanillaBrand(final CallbackInfoReturnable<String> info) {
		if (SpoofAsVanilla.isSpoofing()) {
			info.setReturnValue(ClientBrandRetriever.VANILLA_NAME);
		}
	}
}
