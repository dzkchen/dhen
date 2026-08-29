package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.sound.CustomSoundPack;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public abstract class MinecraftCustomSoundPackMixin {
	@ModifyArg(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"
		),
		index = 0
	)
	private RepositorySource[] dhen$addCustomSoundPack(final RepositorySource[] sources) {
		final RepositorySource[] extended = Arrays.copyOf(sources, sources.length + 1);
		extended[sources.length] = CustomSoundPack.repositorySource();
		return extended;
	}
}
