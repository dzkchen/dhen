package io.github.dzkchen.dhen.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSystemChatPacket.class)
public interface SystemChatPacketAccessor {
	@Accessor("content")
	Component chatContent();

	@Mutable
	@Accessor("content")
	void chatContent(Component content);

	@Accessor("overlay")
	boolean chatOverlay();
}
