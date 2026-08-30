package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.dzkchen.dhen.features.visual.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@WrapWithCondition(
		method = "turn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;onPassengerTurned(Lnet/minecraft/world/entity/Entity;)V"
		)
	)
	private boolean dhen$ridingTurn(final Entity vehicle, final Entity passenger) {
		Camera.fixRidingTurn(vehicle, passenger);
		return true;
	}
}
