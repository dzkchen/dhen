package io.github.dzkchen.dhen.mixin;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundChatPacket.class)
public interface ServerboundChatPacketAccessor extends ChatTextAccess {
	@Override
	@Accessor("message")
	String chatText();

	@Override
	@Mutable
	@Accessor("message")
	void chatText(String text);
}
