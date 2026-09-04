package io.github.dzkchen.dhen.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> chatTrimmedMessages();

	@Accessor("allMessages")
	List<GuiMessage> chatAllMessages();

	@Accessor("chatScrollbarPos")
	int chatScrollbarPos();

	@Accessor("chatScrollbarPos")
	void chatScrollbarPos(int position);

	@Invoker("refreshTrimmedMessages")
	void chatRefreshTrimmed();
}
