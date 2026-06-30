package io.github.dzkchen.dhen.platform

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.dzkchen.dhen.Dhen

class DhenModMenu : ModMenuApi {
	override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
		ConfigScreenFactory<DhenScreen> { parent -> DhenScreen(parent, Dhen.runtime) }
}
