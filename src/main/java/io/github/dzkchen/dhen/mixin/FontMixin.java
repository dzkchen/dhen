package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.text.TextRewrite;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public class FontMixin {
	@ModifyVariable(
		method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
		at = @At("HEAD"),
		argsOnly = true
	)
	private String dhen$prepareString(final String text) {
		return TextRewrite.rewriting() ? TextRewrite.string(text) : text;
	}

	@ModifyVariable(
		method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
		at = @At("HEAD"),
		argsOnly = true
	)
	private FormattedCharSequence dhen$prepareSequence(final FormattedCharSequence text) {
		return TextRewrite.rewriting() ? TextRewrite.sequence(text) : text;
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
	private String dhen$widthOfString(final String text) {
		return TextRewrite.rewriting() ? TextRewrite.string(text) : text;
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText dhen$widthOfText(final FormattedText text) {
		if (!TextRewrite.rewriting() || !(text instanceof Component component)) {
			return text;
		}
		return TextRewrite.component(component);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence dhen$widthOfSequence(final FormattedCharSequence text) {
		return TextRewrite.rewriting() ? TextRewrite.sequence(text) : text;
	}
}
