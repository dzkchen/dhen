package io.github.dzkchen.dhen.mixin;

import io.github.dzkchen.dhen.privacy.ClientLanguageAccess;
import java.util.Map;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLanguage.class)
public interface ClientLanguageAccessor extends ClientLanguageAccess {
	@Override
	@Accessor("storage")
	Map<String, String> dhenTranslations();
}
