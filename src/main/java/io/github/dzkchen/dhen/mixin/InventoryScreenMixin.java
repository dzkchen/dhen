package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.inventory.EquipmentSlots;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@ModifyArg(
		method = "getRecipeBookButtonPosition",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/navigation/ScreenPosition;<init>(II)V"
		),
		index = 0
	)
	private int dhen$clearEquipmentColumn(final int x) {
		return x + EquipmentSlots.buttonShift();
	}
}
