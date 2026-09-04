package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.dzkchen.dhen.privacy.PackControls;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@WrapOperation(
		method = "rebuildSelected",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/repository/Pack;isRequired()Z"
		)
	)
	private boolean dhen$releaseUnselectedServerPack(final Pack pack, final Operation<Boolean> original) {
		if (!original.call(pack)) return false;
		if (pack.getPackSource() != PackSource.SERVER) return true;
		return !PackControls.staysUnselected(pack.getId());
	}

	@ModifyReturnValue(method = "openAllSelected", at = @At("RETURN"))
	private List<PackResources> dhen$loadServerPacksFirst(final List<PackResources> opened) {
		if (!PackControls.loadsServerPacksFirst()) return opened;
		final List<PackResources> reordered = new ArrayList<>(opened.size());
		for (final PackResources pack : opened) {
			if (PackControls.isServerPack(pack.packId())) reordered.add(pack);
		}
		if (reordered.isEmpty() || reordered.size() == opened.size()) return opened;
		for (final PackResources pack : opened) {
			if (!PackControls.isServerPack(pack.packId())) reordered.add(pack);
		}
		return reordered;
	}
}
