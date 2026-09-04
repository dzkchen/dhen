package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dzkchen.dhen.privacy.PackControls;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin {
	@ModifyExpressionValue(
		method = "handlePackSelection",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/repository/PackCompatibility;isCompatible()Z"
		)
	)
	private boolean dhen$skipMismatchScreen(final boolean compatible) {
		return compatible || PackControls.skipsMismatchScreen();
	}
}
