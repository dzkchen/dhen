package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.event.RenderHooks;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@ModifyReturnValue(
		method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
		at = @At("RETURN")
	)
	private boolean dhen$afterShouldRender(
		final boolean rendering,
		final Entity entity,
		final Frustum frustum,
		final double cameraX,
		final double cameraY,
		final double cameraZ
	) {
		return rendering && !RenderHooks.entityRenderCancelled(entity);
	}

	@ModifyReturnValue(
		method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
		at = @At("RETURN")
	)
	private EntityRenderState dhen$afterExtractEntity(
		final EntityRenderState state,
		final Entity entity,
		final float partialTick
	) {
		state.outlineColor = RenderHooks.entityOutline(entity, state.outlineColor);
		if (state.nameTag != null) {
			state.nameTag = RenderHooks.entityNameTag(entity, state.nameTag);
		}
		return state;
	}
}
