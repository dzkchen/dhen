package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.PackControls;
import java.util.List;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

	@Inject(method = "commit", at = @At("HEAD"))
	private void dhen$rememberServerPackChoice(final CallbackInfo callback) {
		if (!PackControls.unpinsPacks()) return;
		for (final Pack pack : this.selected) {
			dhen$record(pack, true);
		}
		for (final Pack pack : this.unselected) {
			dhen$record(pack, false);
		}
	}

	@Unique
	private void dhen$record(final Pack pack, final boolean chosen) {
		if (pack.getPackSource() == PackSource.SERVER) {
			PackControls.recordSelection(pack.getId(), chosen);
		}
	}
}
