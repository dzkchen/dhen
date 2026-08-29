package io.github.dzkchen.dhen.gui

import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.sound.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SoundRuleEditorTest {
	@TempDir
	lateinit var directory: Path

	private val editor = SoundRuleEditor()

	@BeforeEach
	fun install() = SoundManager.install(
		ConfigStore(
			directory.resolve("sounds.json"),
			CoroutineScope(Dispatchers.Unconfined),
			SoundManager.migrations,
			SoundManager.authoritative,
			debounce = {}
		)
	)

	@AfterEach
	fun release() = SoundManager.uninstall()

	@Test
	fun `choosing a replacement lifts a muted sound off mute so the substitute is audible`() {
		val arrow = sound(SoundEvents.ARROW_HIT_PLAYER.location())
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		SoundManager.setVolumePercent(arrow.identifier, 0)
		editor.open(arrow)

		editor.select(harp)

		assertEquals(100, SoundManager.getVolumePercent(arrow.identifier))
		assertEquals(harp, SoundManager.replacementOf(arrow.identifier))
	}

	@Test
	fun `the open panel swallows clicks inside it and closes on a click outside`() {
		editor.open(sound(SoundEvents.ARROW_HIT_PLAYER.location()))

		assertEquals(RuleAction.CONSUMED, editor.press(2, 2, 0, 0))
		assertEquals(RuleAction.CLOSE, editor.press(RULE_PANEL_WIDTH + 1, 2, 0, 0))
		assertEquals(RuleAction.CLOSE, editor.press(2, RULE_PANEL_HEIGHT + 1, 0, 0))
	}

	private fun sound(identifier: Identifier): ManagedSound = ManagedSound(
		identifier,
		soundCleanName(identifier),
		soundCategory(identifier),
		SoundEvent.createVariableRangeEvent(identifier)
	)

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
