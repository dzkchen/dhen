package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.gui.DhenFont;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.client.gui.font.FontManager$CachedFontProvider")
public abstract class FontManagerMixin {
	@ModifyVariable(method = "glyphs", at = @At("HEAD"), argsOnly = true)
	private FontDescription dhen$resolveDefault(final FontDescription description) {
		return DhenFont.resolve(description);
	}
}
