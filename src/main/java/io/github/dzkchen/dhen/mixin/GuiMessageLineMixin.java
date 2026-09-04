package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.dzkchen.dhen.features.chat.ChatTweaks;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiMessage.Line.class)
public abstract class GuiMessageLineMixin {
	@ModifyReturnValue(method = "tag", at = @At("RETURN"))
	private GuiMessageTag dhen$hideMessageTag(final GuiMessageTag original) {
		return ChatTweaks.hidesMessageTag() ? null : original;
	}
}
