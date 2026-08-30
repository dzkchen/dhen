package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.PacketOrigin;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TranslatableContents.class)
public class TranslatableContentsMixin implements PacketOrigin {
	@Unique
	private boolean dhen$fromPacket;

	@Override
	public boolean fromPacket() {
		return this.dhen$fromPacket;
	}

	@Override
	public void markFromPacket() {
		this.dhen$fromPacket = true;
	}
}
