package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.dzkchen.dhen.features.qol.Tweaks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindsListEntryMixin {
	@WrapOperation(
		method = "refreshEntry",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z")
	)
	private boolean dhen$sameKeyMapping(
		final KeyMapping mapping,
		final KeyMapping other,
		final Operation<Boolean> original
	) {
		return !Tweaks.allowsDuplicateKeybinds() && original.call(mapping, other);
	}
}
