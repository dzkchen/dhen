package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.WorldHooks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$EntityCallbacks")
public abstract class ClientLevelEntityCallbacksMixin {
	@Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
	private void dhen$onTrackingEnd(final Entity entity, final CallbackInfo callback) {
		WorldHooks.entityUnloaded(entity);
	}
}
