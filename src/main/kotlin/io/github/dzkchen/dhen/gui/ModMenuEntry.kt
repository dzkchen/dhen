package io.github.dzkchen.dhen.gui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.dzkchen.dhen.Dhen

object ModMenuEntry : ModMenuApi {
	override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
		ConfigScreenFactory { parent -> Dhen.clickGuiScreen(parent) ?: parent }
}
