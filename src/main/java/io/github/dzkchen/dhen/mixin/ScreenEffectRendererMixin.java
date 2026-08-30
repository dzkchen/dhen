package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.dzkchen.dhen.features.visual.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Shadow
	private static @Nullable BlockState getViewBlockingState(final Player player) {
		throw new AssertionError();
	}

	@Redirect(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;getViewBlockingState(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private BlockState dhen$viewBlockingState(final Player player) {
		return Camera.shouldHideBlockOverlay() ? null : getViewBlockingState(player);
	}

	@WrapWithCondition(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitWater(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
		)
	)
	private boolean dhen$showWater(
		final Minecraft minecraft,
		final PoseStack pose,
		final SubmitNodeCollector submitter
	) {
		return !Camera.shouldHideWaterOverlay();
	}

	@WrapWithCondition(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
		)
	)
	private boolean dhen$showFire(
		final PoseStack pose,
		final SubmitNodeCollector submitter,
		final TextureAtlasSprite sprite
	) {
		return !Camera.shouldHideFireOverlay();
	}
}
