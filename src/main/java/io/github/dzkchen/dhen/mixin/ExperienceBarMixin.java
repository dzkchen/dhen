package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.visual.PlayerStatsHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void dhen$hideExperienceBar(
		final GuiGraphicsExtractor graphics,
		final DeltaTracker deltaTracker,
		final CallbackInfo callback
	) {
		if (PlayerStatsHud.shouldHideExperience()) {
			callback.cancel();
		}
	}
}
