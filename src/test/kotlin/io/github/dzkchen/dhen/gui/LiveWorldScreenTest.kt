package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.uninitialized
import io.github.dzkchen.dhen.ui.hud.HudEditorScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveWorldScreenTest {
	@Test
	fun `a live world screen neither pauses nor closes in a portal`() {
		val screen = object : LiveWorldScreen(uninitialized<Minecraft>(), uninitialized<Font>(), Component.empty()) {}

		assertFalse(screen.isPauseScreen)
		assertTrue(screen.isAllowedInPortal)
	}

	@Test
	fun `both Dhen world overlays share the live screen contract`() {
		assertSame(LiveWorldScreen::class.java, ClickGuiShellScreen::class.java.superclass)
		assertSame(LiveWorldScreen::class.java, HudEditorScreen::class.java.superclass)
		assertSame(LiveWorldScreen::class.java, ServerPackConsentScreen::class.java.superclass)
	}
}
