package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.sound.CustomSoundPack;
import java.util.List;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {
	@Shadow
	@Final
	private List<Pack> selected;

	@Shadow
	@Final
	private List<Pack> unselected;

	@Inject(method = {"<init>", "findNewPacks"}, at = @At("RETURN"))
	private void dhen$hideCustomSoundPack(final CallbackInfo info) {
		this.selected.removeIf(CustomSoundPack::isOwnPack);
		this.unselected.removeIf(CustomSoundPack::isOwnPack);
	}
}
