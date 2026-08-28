package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.qol.ArrowFix;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {
	protected PlayerMixin(final EntityType<? extends LivingEntity> entityType, final Level level) {
		super(entityType, level);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void dhen$stopShortbowUse(final CallbackInfo callback) {
		if ((Object)this != Minecraft.getInstance().player || !ArrowFix.shouldStop(this.useItem)) {
			return;
		}
		this.useItem = ItemStack.EMPTY;
		this.useItemRemaining = 0;
	}
}
