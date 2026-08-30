package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.dzkchen.dhen.privacy.PacketOrigin;
import io.github.dzkchen.dhen.privacy.SilentPacketTranslation;
import io.github.dzkchen.dhen.privacy.TranslationProtection;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TranslatableContents.class)
public class TranslatableContentsMixin implements PacketOrigin, SilentPacketTranslation {
	@Unique
	private boolean dhen$fromPacket;
	@Unique
	private boolean dhen$reported;
	@Unique
	private boolean dhen$silent;
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

	@Override
	public void markSilent() {
		this.dhen$silent = true;
	}

	@WrapOperation(
		method = "decompose",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;"
		)
	)
	private String dhen$getOrDefault(
		final Language language,
		final String key,
		final Operation<String> original
	) {
		final String result = this.dhen$resolve(language, key, key);
		return result == TranslationProtection.ALLOW_ORIGINAL ? original.call(language, key) : result;
	}

	@WrapOperation(
		method = "decompose",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
		)
	)
	private String dhen$getOrDefault(
		final Language language,
		final String key,
		final String defaultValue,
		final Operation<String> original
	) {
		final String result = this.dhen$resolve(language, key, defaultValue);
		return result == TranslationProtection.ALLOW_ORIGINAL ? original.call(language, key, defaultValue) : result;
	}

	@Unique
	private String dhen$resolve(final Language language, final String key, final String defaultValue) {
		final boolean singleplayer = Minecraft.getInstance().hasSingleplayerServer();
		if (!this.dhen$fromPacket || singleplayer || !TranslationProtection.active()) {
			return TranslationProtection.ALLOW_ORIGINAL;
		}
		final boolean protecting = TranslationProtection.protecting();
		final String result = TranslationProtection.resolve(
			key,
			defaultValue,
			true,
			false,
			protecting,
			protecting && TranslationProtection.vanillaMode()
		);
		TranslationProtection.notifyExploitDetected();
		this.dhen$report(language, key, defaultValue, result);
		return result;
	}

	@Unique
	private void dhen$report(
		final Language language,
		final String key,
		final String defaultValue,
		final String result
	) {
		if (this.dhen$silent || this.dhen$reported) {
			return;
		}
		final boolean blocked = result != TranslationProtection.ALLOW_ORIGINAL;
		if (!TranslationProtection.shouldReport(blocked)) {
			return;
		}
		this.dhen$reported = true;
		final String original = TranslationProtection.realValue(language, key, defaultValue);
		if (blocked && !original.equals(result)) {
			TranslationProtection.sendDetail(TranslationProtection.Type.TRANSLATION, key, original, result, this.packetName());
		} else {
			TranslationProtection.sendDetailDebug(TranslationProtection.Type.TRANSLATION, key, original, original, this.packetName());
		}
		TranslationProtection.logDetection(
			TranslationProtection.Type.TRANSLATION,
			key,
			original,
			blocked ? result : original,
			this.packetName()
		);
	}
}
