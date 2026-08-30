package io.github.dzkchen.dhen.features.privacy

import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import io.github.dzkchen.dhen.privacy.ServerPacks
import io.github.dzkchen.dhen.privacy.ShaderStripTracker
import io.github.dzkchen.dhen.privacy.TrackPackDetector
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen

object ServerPackBypass : Module(
	name = "Server Pack Bypass",
	category = Category.PRIVACY,
	description = "Decides how much of a server's resource pack is allowed to reach your game."
) {
	private val modeSetting = SelectorSetting(
		"Mode",
		ServerPacks.MANUAL,
		ServerPacks.CHOOSABLE_MODES,
		listed = true,
		description = "Manual keeps a server pack's own look; Always On keeps only its language files. " +
			"Minecraft still asks whether to download it — say yes, and the pack is reported as loaded " +
			"while none of it reaches you, so a server that demands one cannot kick you over it."
	).also { it.changed = ::publish }
	private var chosenMode by modeSetting

	init {
		on<GuiOpenEvent> { if (it.screen is ConnectScreen) TrackPackDetector.reset() }
		on<ClientTickEvent.End> { ShaderStripTracker.flushPending(Minecraft.getInstance().player != null) }
		on<WorldChangeEvent> { if (it.phase == WorldChange.DISCONNECT) ServerPacks.forgetAll() }
	}

	override fun onEnabled() {
		publish()
	}

	override fun onDisabled() {
		publish()
		ServerPacks.forgetAll()
		TrackPackDetector.reset()
	}

	override fun onReset() {
		publish()
	}

	private fun publish() {
		ServerPacks.mode = ServerPacks.effectiveMode(enabled, chosenMode)
	}
}
