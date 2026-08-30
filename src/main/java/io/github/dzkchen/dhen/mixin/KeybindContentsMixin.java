package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.dzkchen.dhen.privacy.KeybindDefaults;
import io.github.dzkchen.dhen.privacy.LanguageKeys;
import io.github.dzkchen.dhen.privacy.PacketOrigin;
import io.github.dzkchen.dhen.privacy.SilentPacketTranslation;
import io.github.dzkchen.dhen.privacy.TranslationProtection;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeybindContents.class)
public class KeybindContentsMixin implements PacketOrigin {
	@Shadow
	@Final
	private String name;

	@Unique
	private boolean dhen$fromPacket;
	@Unique
	private boolean dhen$reported;
	@Unique
	private Component dhen$cachedBlocked;
	@Unique
	private boolean dhen$cachedDefault;
	@Unique
	private long dhen$cachedDefaultGeneration;
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

	@WrapOperation(
		method = "getNestedComponent",
		at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
	)
	private Object dhen$resolveKeybind(final Supplier<?> supplier, final Operation<Object> original) {
		final boolean singleplayer = Minecraft.getInstance().hasSingleplayerServer();
		if (!this.dhen$fromPacket || singleplayer || !TranslationProtection.active()) {
			return original.call(supplier);
		}
		if (
			this.dhen$cachedBlocked != null
				&& (!this.dhen$cachedDefault || this.dhen$cachedDefaultGeneration == KeybindDefaults.generation())
		) {
			return this.dhen$cachedBlocked;
		}

		TranslationProtection.notifyExploitDetected();
		final boolean protecting = TranslationProtection.protecting();
		final boolean whitelisted = LanguageKeys.isWhitelistedKey(this.name);
		final InputConstants.Key defaultKey = protecting && !whitelisted ? KeybindDefaults.defaultKey(this.name) : null;
		final TranslationProtection.KeybindResolution resolution = TranslationProtection.resolveKeybind(
			true,
			false,
			protecting,
			whitelisted,
			defaultKey != null,
			TranslationProtection.fakeDefaultKeybinds()
		);

		if (resolution == TranslationProtection.KeybindResolution.ORIGINAL) {
			return this.dhen$passThrough(supplier, original);
		}
		if (resolution == TranslationProtection.KeybindResolution.DEFAULT) {
			final String spoofed = defaultKey.getDisplayName().getString();
			this.dhen$reportBlocked(spoofed);
			this.dhen$cachedBlocked = Component.literal(spoofed);
			this.dhen$cachedDefault = true;
			this.dhen$cachedDefaultGeneration = KeybindDefaults.generation();
			return this.dhen$cachedBlocked;
		}

		final String serverPackValue = LanguageKeys.serverPackValue(this.name);
		final String spoofed = serverPackValue == null ? this.name : serverPackValue;
		this.dhen$reportBlocked(spoofed);
		final Component replacement = Component.translatable(this.name);
		final Object contents = replacement.getContents();
		if (contents instanceof PacketOrigin origin) {
			origin.markFromPacket();
		}
		if (contents instanceof SilentPacketTranslation silent) {
			silent.markSilent();
		}
		this.dhen$cachedBlocked = replacement;
		this.dhen$cachedDefault = false;
		return replacement;
	}

	@Unique
	private Object dhen$passThrough(final Supplier<?> supplier, final Operation<Object> original) {
		final Object result = original.call(supplier);
		if (this.dhen$reported || !TranslationProtection.shouldReport(false)) {
			return result;
		}
		this.dhen$reported = true;
		final String display = result instanceof Component component ? component.getString() : this.name;
		TranslationProtection.sendDetailDebug(
			TranslationProtection.Type.KEYBIND,
			this.name,
			display,
			display,
			this.packetName()
		);
		TranslationProtection.logDetection(
			TranslationProtection.Type.KEYBIND,
			this.name,
			display,
			display,
			this.packetName()
		);
		return result;
	}

	@Unique
	private void dhen$reportBlocked(final String spoofed) {
		if (this.dhen$reported || !TranslationProtection.shouldReport(true)) {
			return;
		}
		this.dhen$reported = true;
		final String original = TranslationProtection.realKeybindValue(this.name);
		if (original.equals(spoofed)) {
			TranslationProtection.sendDetailDebug(
				TranslationProtection.Type.KEYBIND,
				this.name,
				original,
				spoofed,
				this.packetName()
			);
		} else {
			TranslationProtection.sendDetail(
				TranslationProtection.Type.KEYBIND,
				this.name,
				original,
				spoofed,
				this.packetName()
			);
		}
		TranslationProtection.logDetection(
			TranslationProtection.Type.KEYBIND,
			this.name,
			original,
			spoofed,
			this.packetName()
		);
	}
}
