package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.dzkchen.dhen.privacy.LangOnlyPack;
import io.github.dzkchen.dhen.privacy.ServerPacks;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DownloadedPackSource.class)
public abstract class DownloadedPackSourceMixin {
	@WrapOperation(
		method = "loadRequestedPacks",
		at = @At(
			value = "NEW",
			target = "(Ljava/nio/file/Path;)Lnet/minecraft/server/packs/FilePackResources$FileResourcesSupplier;"
		)
	)
	private FilePackResources.FileResourcesSupplier dhen$stripServerPack(
		final Path file,
		final Operation<FilePackResources.FileResourcesSupplier> original,
		@Local final PackReloadConfig.IdAndPath requested
	) {
		final FilePackResources.FileResourcesSupplier supplier = original.call(file);
		final UUID id = requested.id();
		if (!ServerPacks.isWrapped(id)) return supplier;
		return new FilePackResources.FileResourcesSupplier(file) {
			@Override
			public PackResources openPrimary(final PackLocationInfo location) {
				return LangOnlyPack.over(supplier.openPrimary(location), id);
			}

			@Override
			public PackResources openFull(final PackLocationInfo location, final Pack.Metadata metadata) {
				return LangOnlyPack.over(supplier.openFull(location, metadata), id);
			}
		};
	}
}
