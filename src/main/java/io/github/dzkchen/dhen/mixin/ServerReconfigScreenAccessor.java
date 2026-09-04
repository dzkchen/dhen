package io.github.dzkchen.dhen.mixin;

import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerReconfigScreen.class)
public interface ServerReconfigScreenAccessor {
	@Accessor("connection")
	Connection reconfigureConnection();
}
