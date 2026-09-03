package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
	fun install() = SoundManager.install(soundStore(directory.resolve("sounds.json")))

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

	@Test
	fun `a Recent row edits its full pitch while a catalogue row edits the unbound rule`() {
		val recent = sound(SoundEvents.FLINTANDSTEEL_USE.location(), 0.74603176f)
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		editor.open(recent)
		editor.select(harp)

		assertEquals(harp, SoundManager.replacementOf(recent.identifier, 0.74603176f))
		assertNull(SoundManager.replacementOf(recent.identifier))

		editor.open(sound(SoundEvents.FLINTANDSTEEL_USE.location()))
		editor.select(harp)

		assertEquals(harp, SoundManager.replacementOf(recent.identifier))
	}

	private fun sound(identifier: Identifier, matchPitch: Float = Float.NaN): ManagedSound = ManagedSound(
		identifier,
		soundCleanName(identifier),
		soundCategory(identifier),
		SoundEvent.createVariableRangeEvent(identifier),
		matchPitch
	)

	companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
