package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.dzkchen.dhen.features.visual.Animations;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	private float oMainHandHeight;

	@Shadow
	private float mainHandHeight;

	@Shadow
	private float oOffHandHeight;

	@Shadow
	private float offHandHeight;

	@Inject(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
			shift = At.Shift.AFTER
		)
	)
	private void dhen$applyHandOffset(
		final AbstractClientPlayer player,
		final float frameInterp,
		final float xRot,
		final InteractionHand hand,
		final float attack,
		final ItemStack itemStack,
		final float inverseArmHeight,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		Animations.applyHandOffset(poseStack, hand, itemStack);
	}

	@ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private float dhen$modifySwingProgress(
		final float attack,
		@Local(argsOnly = true) final ItemStack itemStack
	) {
		return Animations.swingProgress(attack, itemStack);
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
		)
	)
	private void dhen$applyItemTransform(
		final AbstractClientPlayer player,
		final float frameInterp,
		final float xRot,
		final InteractionHand hand,
		final float attack,
		final ItemStack itemStack,
		final float inverseArmHeight,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		Animations.applyItemTransform(poseStack);
	}

	@Redirect(
		method = "swingArm",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"
		)
	)
	private void dhen$applySwingOffset(
		final PoseStack poseStack,
		final float xOffset,
		final float yOffset,
		final float zOffset
	) {
		Animations.applySwingOffset(poseStack, xOffset, yOffset, zOffset);
	}

	@Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
	private void dhen$disableEquipAnimation(
		final ItemStack currentlyVisibleItem,
		final ItemStack expectedItem,
		final CallbackInfoReturnable<Boolean> cir
	) {
		if (Animations.disableEquipAnimation()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void dhen$keepHeldItemsRaised(final CallbackInfo ci) {
		if (!Animations.disableEquipAnimation()) return;
		this.oMainHandHeight = 1.0F;
		this.mainHandHeight = 1.0F;
		this.oOffHandHeight = 1.0F;
		this.offHandHeight = 1.0F;
	}

	@WrapWithCondition(
		method = "submitHandsWithItems",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"
		)
	)
	private boolean dhen$allowHandMovement(final PoseStack poseStack, final Quaternionfc rotation) {
		return !Animations.disableHandMovement();
	}
}
