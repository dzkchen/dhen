package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.InteractionHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(
		method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
		at = @At("RETURN")
	)
	private void dhen$afterUseItemOn(
		final LocalPlayer player,
		final InteractionHand hand,
		final BlockHitResult hit,
		final CallbackInfoReturnable<InteractionResult> callback
	) {
		InteractionHooks.usedBlock(hand, hit, callback.getReturnValue());
	}
}
