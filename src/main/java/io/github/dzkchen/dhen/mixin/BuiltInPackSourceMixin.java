package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.font.DhenFontPack;
import java.util.function.Consumer;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltInPackSource.class)
public abstract class BuiltInPackSourceMixin {
	@Inject(method = "loadPacks", at = @At("RETURN"))
	private void dhen$addFontPack(final Consumer<Pack> packs, final CallbackInfo info) {
		if ((Object) this instanceof ClientPackSource) DhenFontPack.contribute(packs);
	}
}
