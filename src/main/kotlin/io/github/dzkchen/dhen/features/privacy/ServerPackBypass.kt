package io.github.dzkchen.dhen.features.privacy

import io.github.dzkchen.dhen.config.SelectorSetting
import io.github.dzkchen.dhen.event.ClientTickEvent
import io.github.dzkchen.dhen.event.GuiOpenEvent
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.ServerPackConsentScreen
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
		description = "Manual keeps Minecraft's prompt and the full pack. Ask starts stripped and lets you load it fully once per session. " +
			"Always On silently accepts the pack but keeps only its language files."
	).also { it.changed = ::publish }
	private var chosenMode by modeSetting

	init {
		on<GuiOpenEvent> { if (it.screen is ConnectScreen) TrackPackDetector.reset() }
		on<ClientTickEvent.End> {
			val client = Minecraft.getInstance()
			ShaderStripTracker.flushPending(client.player != null)
			ServerPackConsentScreen.tryShow(client)
		}
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
