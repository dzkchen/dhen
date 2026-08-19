package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.event.ChatTextAccess;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundChatCommandPacket.class)
public interface ServerboundChatCommandPacketAccessor extends ChatTextAccess {
	@Override
	@Accessor("command")
	String chatText();

	@Override
	@Mutable
	@Accessor("command")
	void chatText(String text);
}
