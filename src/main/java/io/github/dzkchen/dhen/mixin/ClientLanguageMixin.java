package io.github.dzkchen.dhen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.dzkchen.dhen.privacy.KeybindDefaults;
import io.github.dzkchen.dhen.privacy.LanguageKeys;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
	@WrapMethod(method = "loadFrom")
	private static ClientLanguage dhen$trackReload(
		final ResourceManager resourceManager,
		final List<String> languageStack,
		final boolean defaultRightToLeft,
		final Operation<ClientLanguage> original
	) {
		LanguageKeys.beginReload();
		try {
			final ClientLanguage language = original.call(resourceManager, languageStack, defaultRightToLeft);
			LanguageKeys.commitReload();
			KeybindDefaults.reset();
			return language;
		} catch (RuntimeException | Error exception) {
			LanguageKeys.abortReload();
			throw exception;
		}
	}

	@WrapOperation(
		method = "appendFrom",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"
		)
	)
	private static void dhen$trackKeys(
		final InputStream stream,
		final BiConsumer<String, String> output,
		final Operation<Void> original,
		@Local final Resource resource
	) {
		original.call(stream, LanguageKeys.trackingConsumer(resource.source(), output));
	}
}
