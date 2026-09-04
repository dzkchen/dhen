package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.text.TextRewrite;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StringSplitter.class)
public class StringSplitterMixin {
	@ModifyVariable(
		method = "splitLines(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/network/chat/Style;Ljava/util/function/BiConsumer;)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private FormattedText dhen$splitReplaced(final FormattedText text) {
		return TextRewrite.rewriting() ? TextRewrite.wrapping(text) : text;
	}
}
