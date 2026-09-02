package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.dzkchen.dhen.event.RenderHooks;
import io.github.dzkchen.dhen.features.visual.RenderOptimizer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
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

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"
		)
	)
	private void dhen$submitFlame(
		final SubmitNodeCollector collector,
		final PoseStack poseStack,
		final EntityRenderState state,
		final Quaternionf rotation,
		final Operation<Void> original
	) {
		if (RenderOptimizer.hidesFireOnEntities()) {
			return;
		}
		original.call(collector, poseStack, state, rotation);
	}
}
