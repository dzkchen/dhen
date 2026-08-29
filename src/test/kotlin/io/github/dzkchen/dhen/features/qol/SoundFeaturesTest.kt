package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.module.ModuleManager
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SoundFeaturesTest {
	private lateinit var manager: ModuleManager
	private lateinit var originalPlay: () -> Unit

	@BeforeEach
	fun bindModules() {
		manager = ModuleManager()
		manager.registerAll(ArrowHitSound)
		originalPlay = ArrowHitSound.soundSettings.play.value
	}

	@AfterEach
	fun unbindModules() {
		ArrowHitSound.soundSettings.play.value = originalPlay
		manager.unregister(ArrowHitSound)
	}

	@Test
	fun `arrow hit replacement is enabled exact and recursion safe`() {
		val arrowHit = SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1f, 1f)
		val other = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), 1f, 1f)
		var requested = 0
		ArrowHitSound.soundSettings.play.value = {
			requested++
		}

		assertFalse(ArrowHitSound.onSoundPlay(arrowHit))
		manager.enable(ArrowHitSound)
		assertFalse(ArrowHitSound.onSoundPlay(other))
		assertTrue(ArrowHitSound.onSoundPlay(arrowHit))
		assertEquals(1, requested)

		var plays = 0
		ArrowHitSound.playReplacement(SoundEvents.ARROW_HIT_PLAYER, 0.5f, 1f) { nested ->
			plays++
			assertFalse(ArrowHitSound.onSoundPlay(nested))
		}
		assertEquals(5, plays)
		assertEquals(listOf("Sound", "Volume", "Pitch", "Play Sound"), ArrowHitSound.settings.map { it.name })
	}
}
