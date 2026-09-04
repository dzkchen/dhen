package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.config.BooleanSetting
import io.github.dzkchen.dhen.event.InputAction
import io.github.dzkchen.dhen.event.MouseInputEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.util.EtherwarpGuess
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import org.lwjgl.glfw.GLFW

object LCEtherwarp : Module(
	name = "Left-Click Etherwarp",
	category = Category.QOL,
	description = "Turns a left click into the right click that fires Etherwarp while you sneak."
) {
	private var swingHand by BooleanSetting(
		"Swing Hand",
		default = true,
		description = "Plays the arm swing when the click goes through."
	)

	init {
		on<MouseInputEvent> { clicked(it) }
	}

	private fun clicked(event: MouseInputEvent) {
		if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.action != InputAction.PRESS) return
		val client = Minecraft.getInstance()
		if (client.gui.screen() != null) return
		val player = client.player ?: return
		val sneaking = client.options.keyShift.isDown
		if (!sneaking && !EtherwarpAutoSneak.enabled) return
		if (client.options.keyUse.isUnbound) return
		if (EtherwarpGuess.etherwarpItem(player.mainHandItem) == null) return

		event.cancelled = true
		if (!sneaking) EtherwarpAutoSneak.sneakAround(::useHeldItem)
		else useHeldItem()
	}

	private fun useHeldItem() {
		val client = Minecraft.getInstance()
		val useKey = client.options.keyUse
		if (useKey.isUnbound) return
		KeyMapping.click(KeyMappingHelper.getBoundKeyOf(useKey))
		if (swingHand) client.player?.swing(InteractionHand.MAIN_HAND, false)
	}
}
