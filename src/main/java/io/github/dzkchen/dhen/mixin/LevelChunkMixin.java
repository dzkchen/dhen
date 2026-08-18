package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.event.WorldHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
	@Shadow
	@Final
	private Level level;

	@ModifyReturnValue(
		method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("RETURN")
	)
	private BlockState dhen$afterSetBlockState(
		final BlockState replaced,
		final BlockPos pos,
		final BlockState state,
		final int flags
	) {
		if (replaced != null && Minecraft.getInstance().level == this.level) {
			WorldHooks.blockChanged(pos, replaced, state);
		}
		return replaced;
	}
}
