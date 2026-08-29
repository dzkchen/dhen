package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.gui.ClientPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class SoundManagerTest {
	@TempDir
	lateinit var directory: Path

	@AfterEach
	fun release() = SoundManager.uninstall()

	@Test
	fun `multiplier writes normalize persist and publish to the sound thread`() {
		val path = directory.resolve("sounds.json")
		SoundManager.install(store(path))
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()

		assertEquals(165, SoundManager.setVolumePercent(harp, 163))
		assertEquals(1.65f, SoundManager.getMultiplier(harp))

		val fromSoundThread = AtomicReference<Float>()
		Thread { fromSoundThread.set(SoundManager.getMultiplier(harp)) }.apply {
			start()
			join()
		}
		assertEquals(1.65f, fromSoundThread.get())

		SoundManager.uninstall()
		SoundManager.install(store(path))
		assertEquals(165, SoundManager.getVolumePercent(harp))
		assertEquals(1.65f, SoundManager.getMultiplier(harp))
		assertEquals(0, SoundManager.setVolumePercent(harp, -20))
		assertEquals(200, SoundManager.setVolumePercent(harp, 240))
	}

	@Test
	fun `an uninstalled manager leaves every sound at its own volume`() {
		assertEquals(1f, SoundManager.getMultiplier(SoundEvents.NOTE_BLOCK_HARP.value().location()))
	}

	@Test
	fun `bad persisted entries are isolated and valid values normalize`() {
		val path = directory.resolve("sounds.json")
		Files.writeString(
			path,
			"""{"multipliers":{"minecraft:block.note_block.harp":1.63,"bad id":0.5,"minecraft:broken":"value"}}"""
		)

		SoundManager.install(store(path))

		assertEquals(165, SoundManager.getVolumePercent(SoundEvents.NOTE_BLOCK_HARP.value().location()))
		assertEquals(100, SoundManager.getVolumePercent(Identifier.withDefaultNamespace("broken")))
	}

	@Test
	fun `recents are unique capped newest first and reset only on mutation`() {
		val before = SoundManager.recentSoundsVersion
		val identifiers = List(101) { Identifier.fromNamespaceAndPath("test", "sound_$it") }
		for (identifier in identifiers) SoundManager.recordPlayedIdentifier(identifier)
		SoundManager.recordPlayedIdentifier(identifiers.last())

		val recent = SoundManager.recentSoundIds()
		assertEquals(100, recent.size)
		assertEquals(identifiers.last(), recent.first())
		assertEquals(identifiers[1], recent.last())
		assertEquals(before + 101, SoundManager.recentSoundsVersion)

		SoundManager.clearRecentSounds()
		assertTrue(SoundManager.recentSoundIds().isEmpty())
		assertEquals(before + 102, SoundManager.recentSoundsVersion)
		SoundManager.clearRecentSounds()
		assertEquals(before + 102, SoundManager.recentSoundsVersion)
	}

	@Test
	fun `manager preview does not enter recents`() {
		SoundManager.playPreview(SoundEvents.NOTE_BLOCK_HARP.value()) { sound ->
			SoundManager.recordPlayedIdentifier(sound.identifier)
		}

		assertTrue(SoundManager.recentSoundIds().isEmpty())
	}

	@Test
	fun `the manager is opened from the Settings tab and owns no module row`() {
		assertEquals(
			listOf("Open Sound Manager"),
			ClientPrefs.sections.single { it.title == "Sounds" }.settings.map { it.name }
		)
	}

	private fun store(path: Path): ConfigStore =
		ConfigStore(path, CoroutineScope(Dispatchers.Unconfined), debounce = {})
}
