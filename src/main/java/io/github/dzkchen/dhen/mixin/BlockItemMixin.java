package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.qol.NoItemPlace;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
	private void dhen$preventPlacement(
		final BlockPlaceContext context,
		final BlockState state,
		final CallbackInfoReturnable<Boolean> callback
	) {
		if (NoItemPlace.shouldPrevent(context)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(
		method = "place",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"
		),
		cancellable = true
	)
	private void dhen$skipPlacementEffects(
		final BlockPlaceContext context,
		final CallbackInfoReturnable<InteractionResult> callback
	) {
		if (NoItemPlace.shouldPrevent(context)) {
			callback.setReturnValue(InteractionResult.SUCCESS);
		}
	}
}
