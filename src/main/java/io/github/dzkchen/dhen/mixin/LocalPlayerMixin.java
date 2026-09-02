package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import io.github.dzkchen.dhen.features.qol.AutoSprint;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
	@Shadow
	public ClientInput input;

	protected LocalPlayerMixin(final ClientLevel level, final GameProfile profile) {
		super(level, profile);
	}

	@ModifyExpressionValue(
		method = "aiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z")
	)
	private boolean dhen$autoSprint(final boolean original) {
		return AutoSprint.forcesSprint(this.isInWater()) || original;
	}

	@ModifyExpressionValue(
		method = "aiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;shouldStopSwimSprinting()Z")
	)
	private boolean dhen$stopForcedSwimSprint(final boolean original) {
		return AutoSprint.stopsForcedSwimSprint(this.input.keyPresses.sprint()) || original;
	}

	@WrapWithCondition(
		method = "aiStep",
		at = @At(
			value = "INVOKE",
			ordinal = 0,
			target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V"
		)
	)
	private boolean dhen$allowDoubleTapSprint(final LocalPlayer player, final boolean sprinting) {
		return !AutoSprint.suppressesDoubleTapSprint(this.isInWater());
	}
}
