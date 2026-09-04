package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.NumberSetting
import io.github.dzkchen.dhen.features.inventory.StorageOverlayScreen
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object NoCursorReset : Module(
	name = "No Cursor Reset",
	category = Category.QOL,
	description = "Keeps the mouse where you left it when a menu reopens."
) {
	internal val unhookTimeoutSetting = NumberSetting(
		"Unhook Timeout",
		150.0,
		0.0,
		1000.0,
		10.0,
		description = "Milliseconds after leaving a menu within which a reopened menu keeps the cursor."
	)

	private var unhookTimeout by unhookTimeoutSetting

	private var grabbedAt = 0L

	@JvmStatic
	fun cursorGrabbed() {
		grabbedAt = System.currentTimeMillis()
	}

	@JvmStatic
	fun keepsCursorPosition(): Boolean =
		enabled &&
			System.currentTimeMillis() - grabbedAt < unhookTimeout &&
			isContainerMenu(Minecraft.getInstance().gui.screen())

	private fun isContainerMenu(screen: Screen?): Boolean =
		screen is AbstractContainerScreen<*> || screen is StorageOverlayScreen
}
