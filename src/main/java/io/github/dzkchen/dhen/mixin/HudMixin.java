package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.inventory.HotbarSlot;
import io.github.dzkchen.dhen.features.inventory.ItemAbilities;
import io.github.dzkchen.dhen.features.qol.Tweaks;
import io.github.dzkchen.dhen.features.visual.Camera;
import io.github.dzkchen.dhen.features.visual.CustomScoreboard;
import io.github.dzkchen.dhen.features.visual.PlayerStatsHud;
import io.github.dzkchen.dhen.features.visual.VisualTweaks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	private static final String TITLE_SCALE = "Lorg/joml/Matrix3x2fStack;scale(FF)Lorg/joml/Matrix3x2f;";

	@Shadow
	public abstract Font getFont();

	@Shadow
	private @Nullable Component title;

	@Shadow
	private @Nullable Component subtitle;

	@ModifyArg(
		method = "extractTitle",
		at = @At(value = "INVOKE", target = TITLE_SCALE, ordinal = 0),
		index = 0
	)
	private float dhen$fitTitleWidth(final float original) {
		return Tweaks.titleScale(getFont(), this.title, original);
	}

	@ModifyArg(
		method = "extractTitle",
		at = @At(value = "INVOKE", target = TITLE_SCALE, ordinal = 0),
		index = 1
	)
	private float dhen$fitTitleHeight(final float original) {
		return Tweaks.titleScale(getFont(), this.title, original);
	}

	@ModifyArg(
		method = "extractTitle",
		at = @At(value = "INVOKE", target = TITLE_SCALE, ordinal = 1),
		index = 0
	)
	private float dhen$fitSubtitleWidth(final float original) {
		return Tweaks.subtitleScale(getFont(), this.subtitle, original);
	}

	@ModifyArg(
		method = "extractTitle",
		at = @At(value = "INVOKE", target = TITLE_SCALE, ordinal = 1),
		index = 1
	)
	private float dhen$fitSubtitleHeight(final float original) {
		return Tweaks.subtitleScale(getFont(), this.subtitle, original);
	}

	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void dhen$hideScoreboard(final CallbackInfo callback) {
		if (CustomScoreboard.shouldHideVanilla()) {
			callback.cancel();
		}
	}

	@Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
	private static void dhen$hideArmor(
		final GuiGraphicsExtractor graphics,
		final Player player,
		final int yLineBase,
		final int numHealthRows,
		final int healthRowHeight,
		final int xLeft,
		final CallbackInfo callback
	) {
		if (PlayerStatsHud.shouldHideArmor()) {
			callback.cancel();
		}
	}

	@Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
	private void dhen$hideHearts(
		final GuiGraphicsExtractor graphics,
		final Player player,
		final int xLeft,
		final int yLineBase,
		final int healthRowHeight,
		final int heartOffsetIndex,
		final float maxHealth,
		final int currentHealth,
		final int oldHealth,
		final int absorption,
		final boolean blink,
		final CallbackInfo callback
	) {
		if (PlayerStatsHud.shouldHideHearts()) {
			callback.cancel();
		}
	}

	@Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
	private void dhen$hideFood(
		final GuiGraphicsExtractor graphics,
		final Player player,
		final int yLineBase,
		final int xRight,
		final CallbackInfo callback
	) {
		if (PlayerStatsHud.shouldHideFood()) {
			callback.cancel();
		}
	}

	@ModifyExpressionValue(
		method = "extractHotbarAndDecorations",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasExperience()Z"
		)
	)
	private boolean dhen$showExperienceLevel(final boolean original) {
		return original && !PlayerStatsHud.shouldHideExperience();
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void dhen$darkModeBehind(
		final GuiGraphicsExtractor graphics,
		final DeltaTracker deltaTracker,
		final CallbackInfo callback
	) {
		VisualTweaks.drawBehindHud(graphics);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void dhen$darkModeOver(
		final GuiGraphicsExtractor graphics,
		final DeltaTracker deltaTracker,
		final CallbackInfo callback
	) {
		VisualTweaks.drawOverHud(graphics);
	}

	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void dhen$hideHotbarTooltip(final CallbackInfo callback) {
		if (Tweaks.shouldHideHotbarTooltip()) {
			callback.cancel();
		}
	}

	@WrapWithCondition(
		method = "extractCameraOverlays",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;extractPortalOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"
		)
	)
	private boolean dhen$showPortal(final Hud hud, final GuiGraphicsExtractor graphics, final float alpha) {
		return !Camera.shouldHidePortalOverlay();
	}

	@ModifyExpressionValue(
		method = "extractCameraOverlays",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"
		)
	)
	private float dhen$nausea(final float original) {
		return Camera.nauseaIntensity(original);
	}

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void dhen$hotbarUnderlay(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final DeltaTracker deltaTracker,
		final Player player,
		final ItemStack stack,
		final int seed,
		final CallbackInfo callback
	) {
		HotbarSlot.tint(graphics, stack, x, y);
	}

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void dhen$hotbarOverlay(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final DeltaTracker deltaTracker,
		final Player player,
		final ItemStack stack,
		final int seed,
		final CallbackInfo callback
	) {
		ItemAbilities.labelHotbarSlot(graphics, stack, x, y);
	}
}
