package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.privacy.PackControls;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$EntryBase")
public abstract class PackEntryBaseMixin {
	@Shadow
	@Final
	private Pack pack;

	@ModifyReturnValue(method = "isRequired", at = @At("RETURN"))
	private boolean dhen$offerUnselect(final boolean required) {
		if (!required || this.pack.getPackSource() != PackSource.SERVER) return required;
		return !PackControls.offersUnselect(this.pack.getId());
	}
}
