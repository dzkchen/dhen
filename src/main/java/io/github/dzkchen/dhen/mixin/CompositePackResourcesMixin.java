package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.CompositePackAccess;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompositePackResources.class)
public interface CompositePackResourcesMixin extends CompositePackAccess {
	@Override
	@Accessor("primaryPackResources")
	PackResources dhenPrimaryPack();
}
