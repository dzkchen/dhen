package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.dzkchen.dhen.features.qol.Tweaks;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin {
	@ModifyExpressionValue(
		method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;"
			+ "Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;isInvisible:Z",
			opcode = Opcodes.GETFIELD
		)
	)
	private boolean dhen$hideItemFrame(final boolean original, @Local(argsOnly = true) final ItemFrame frame) {
		return original || Tweaks.hidesItemFrame(frame);
	}
}
