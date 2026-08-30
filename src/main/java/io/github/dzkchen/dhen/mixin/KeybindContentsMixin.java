package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.PacketOrigin;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(KeybindContents.class)
public class KeybindContentsMixin implements PacketOrigin {
	@Unique
	private boolean dhen$fromPacket;
	@Unique
	private Identifier dhen$packetId;
	@Unique
	private PacketOrigin dhen$decodedNext;

	@Override
	public boolean fromPacket() {
		return this.dhen$fromPacket;
	}

	@Override
	public void markFromPacket() {
		this.dhen$fromPacket = true;
	}

	@Override
	public String packetName() {
		return this.dhen$packetId == null ? "unknown" : this.dhen$packetId.toString();
	}

	@Override
	public PacketOrigin decodedNext() {
		return this.dhen$decodedNext;
	}

	@Override
	public void linkDecoded(final PacketOrigin next) {
		this.dhen$decodedNext = next;
	}

	@Override
	public void identifyPacket(final Identifier id) {
		this.dhen$packetId = id;
	}
}
