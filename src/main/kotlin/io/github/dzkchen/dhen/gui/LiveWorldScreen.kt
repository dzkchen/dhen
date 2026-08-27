package io.github.dzkchen.dhen.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal abstract class LiveWorldScreen : Screen {
	constructor(title: Component) : super(title)

	internal constructor(minecraft: Minecraft, font: Font, title: Component) : super(minecraft, font, title)

	override fun isPauseScreen(): Boolean = false

	override fun isAllowedInPortal(): Boolean = true
}
