package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.features.chat.ChatHistory;
import io.github.dzkchen.dhen.features.chat.ChatSearch;
import io.github.dzkchen.dhen.features.chat.ChatTabs;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyConstant(
		method = {"addMessageToDisplayQueue", "addMessageToQueue"},
		constant = @Constant(intValue = 100)
	)
	private int dhen$historySize(final int vanilla) {
		return ChatHistory.historySize(vanilla);
	}

	@ModifyVariable(
		method = "addMessage(Lnet/minecraft/network/chat/Component;"
			+ "Lnet/minecraft/network/chat/MessageSignature;"
			+ "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
			+ "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private Component dhen$compactRepeats(final Component message) {
		return ChatHistory.compacted((ChatComponent)(Object)this, message);
	}

	@Inject(method = "addMessageToQueue", at = @At("HEAD"))
	private void dhen$noteAdded(final GuiMessage message, final CallbackInfo callback) {
		ChatHistory.added(message);
	}

	@Inject(method = "clearMessages", at = @At("HEAD"))
	private void dhen$forgetRepeats(final boolean clearHistory, final CallbackInfo callback) {
		ChatHistory.cleared();
	}

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
	private void dhen$filterDisplay(final GuiMessage message, final CallbackInfo callback) {
		if (ChatTabs.hides((ChatComponent)(Object)this, message) || ChatSearch.hides(message)) {
			callback.cancel();
		}
	}
}
