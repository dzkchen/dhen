package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.privacy.PackControls;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Pack.class)
public abstract class PackMixin {
	@Shadow
	public abstract Component getDescription();

	@Shadow
	public abstract boolean isRequired();

	@ModifyReturnValue(method = "isFixedPosition", at = @At("RETURN"))
	private boolean dhen$unpinPack(final boolean fixed) {
		return fixed && !PackControls.unpinsPacks();
	}

	@ModifyReturnValue(method = "getDefaultPosition", at = @At("RETURN"))
	private Pack.Position dhen$sinkServerPack(final Pack.Position position) {
		if (!PackControls.sinksHypixelPack() || !this.isRequired()) return position;
		return PackControls.isHypixelPack(this.getDescription().getString()) ? Pack.Position.BOTTOM : position;
	}
}
