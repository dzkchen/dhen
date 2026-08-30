package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.features.visual.Animations;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
	private int dhen$adjustSwingDuration(final int original) {
		return Animations.swingDuration((LivingEntity) (Object) this, original);
	}
}
