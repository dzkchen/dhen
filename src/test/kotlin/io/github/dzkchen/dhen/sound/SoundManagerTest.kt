package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.config.ConfigStore
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class SoundManagerTest {
	@TempDir
	lateinit var directory: Path

	@AfterEach
	fun release() {
		SoundManager.uninstall()
		CustomSoundPack.uninstall()
	}

	@Test
	fun `multiplier writes normalize persist and publish to the sound thread`() {
		val path = directory.resolve("sounds.json")
		SoundManager.install(store(path))
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()

		assertEquals(165, SoundManager.setVolumePercent(harp, 163))
		assertEquals(1.65f, SoundManager.volumeOf(harp))

		val fromSoundThread = AtomicReference<Float>()
		Thread { fromSoundThread.set(SoundManager.volumeOf(harp)) }.apply {
			start()
			join()
		}
		assertEquals(1.65f, fromSoundThread.get())

		SoundManager.uninstall()
		SoundManager.install(store(path))
		assertEquals(165, SoundManager.getVolumePercent(harp))
		assertEquals(1.65f, SoundManager.volumeOf(harp))
		assertEquals(0, SoundManager.setVolumePercent(harp, -20))
		assertEquals(200, SoundManager.setVolumePercent(harp, 240))
	}

	@Test
	fun `an uninstalled manager leaves every sound at its own volume`() {
		assertEquals(1f, SoundManager.volumeOf(SoundEvents.NOTE_BLOCK_HARP.value().location()))
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
	fun `bad persisted rules are isolated`() {
		val path = directory.resolve("sounds.json")
		Files.writeString(
			path,
			"""{"rules":{"bad id":{"volume":0.0},"minecraft:broken":4,"minecraft:block.note_block.harp":{"volume":0.5}}}"""
		)

		SoundManager.install(store(path))

		assertEquals(50, SoundManager.getVolumePercent(SoundEvents.NOTE_BLOCK_HARP.value().location()))
		assertEquals(100, SoundManager.getVolumePercent(Identifier.withDefaultNamespace("broken")))
	}

	@Test
	fun `the multipliers migration lifts old volumes into rules and drops the old object`() {
		val path = directory.resolve("sounds.json")
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		Files.writeString(path, """{"multipliers":{"minecraft:block.note_block.harp":0.4}}""")

		SoundManager.install(store(path))
		assertEquals(40, SoundManager.getVolumePercent(harp))

		assertEquals(45, SoundManager.setVolumePercent(harp, 45))
		val written = Files.readString(path)
		assertFalse(written.contains("multipliers"))
		assertTrue(written.contains("rules"))

		SoundManager.uninstall()
		SoundManager.install(store(path))
		assertEquals(45, SoundManager.getVolumePercent(harp))
	}

	@Test
	fun `a rule is only a rule once it differs from the default`() {
		val path = directory.resolve("sounds.json")
		SoundManager.install(store(path))
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()

		assertFalse(SoundManager.hasRule(harp))
		SoundManager.setVolumePercent(harp, 100)
		assertFalse(SoundManager.hasRule(harp))
		SoundManager.setVolumePercent(harp, 0)
		assertTrue(SoundManager.hasRule(harp))
		SoundManager.setVolumePercent(harp, 165)
		assertTrue(SoundManager.hasRule(harp))
	}

	@Test
	fun `a replacement survives a volume edit and a reload`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		Files.writeString(
			path,
			"""{"rules":{"minecraft:entity.arrow.hit_player":{"volume":1.0,"replacement":"minecraft:block.note_block.harp","replacementVolume":0.75,"replacementPitch":1.5}}}"""
		)
		SoundManager.install(store(path))

		assertTrue(SoundManager.hasRule(arrow))
		assertEquals(Replacement(SoundEvents.NOTE_BLOCK_HARP.value().location(), 0.75f, 1.5f), dispatchedFor(arrow))

		SoundManager.setVolumePercent(arrow, 60)
		SoundManager.uninstall()
		SoundManager.install(store(path))

		assertEquals(60, SoundManager.getVolumePercent(arrow))
		assertEquals(Replacement(SoundEvents.NOTE_BLOCK_HARP.value().location(), 0.75f, 1.5f), dispatchedFor(arrow))
	}

	@Test
	fun `out of range replacement volume and pitch are bounded and absent members fall back`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val orb = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		Files.writeString(
			path,
			"""{"rules":{
				"minecraft:entity.arrow.hit_player":{"replacement":"minecraft:block.note_block.harp","replacementVolume":9.0,"replacementPitch":0.1},
				"minecraft:entity.experience_orb.pickup":{"replacement":"minecraft:block.note_block.harp"}
			}}""".trimIndent()
		)
		SoundManager.install(store(path))

		assertEquals(Replacement(SoundEvents.NOTE_BLOCK_HARP.value().location(), 1f, 0.5f), dispatchedFor(arrow))
		assertEquals(Replacement(SoundEvents.NOTE_BLOCK_HARP.value().location(), 1f, 1f), dispatchedFor(orb))
		assertEquals(100, SoundManager.getVolumePercent(arrow))
	}

	@Test
	fun `mute cancels an absent rule passes and mute wins over a replacement`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		Files.writeString(
			path,
			"""{"rules":{"minecraft:entity.arrow.hit_player":{"volume":0.0,"replacement":"minecraft:block.note_block.harp"}}}"""
		)
		SoundManager.install(store(path))

		var dispatched = 0
		assertTrue(SoundManager.applyRule(arrow) { _, _, _ -> dispatched++ })
		assertEquals(0, dispatched)
		assertFalse(SoundManager.applyRule(harp) { _, _, _ -> dispatched++ })
		assertEquals(0, dispatched)
	}

	@Test
	fun `a pitch bound rule wins inside its epsilon and the unbound rule handles every other pitch`() {
		val path = directory.resolve("sounds.json")
		val sound = SoundEvents.FLINTANDSTEEL_USE.location()
		val pitch = 0.74603176f
		SoundManager.install(store(path))
		SoundManager.setVolumePercent(sound, 35)
		SoundManager.setVolumePercent(sound, 0, pitch)

		assertEquals(0f, SoundManager.volumeOf(sound, pitch + 0.00005f))
		assertTrue(SoundManager.applyRule(sound, pitch + 0.00005f))
		assertEquals(0.35f, SoundManager.volumeOf(sound, pitch + 0.0002f))
		assertFalse(SoundManager.applyRule(sound, pitch + 0.0002f))

		SoundManager.uninstall()
		SoundManager.install(store(path))

		assertEquals(2, SoundManager.ruledSounds().count { it.identifier == sound })
		assertEquals(0f, SoundManager.volumeOf(sound, pitch))
		assertEquals(0.35f, SoundManager.volumeOf(sound, 1f))
	}

	@Test
	fun `a substitute is played once and no rule fires while it is dispatched`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		Files.writeString(
			path,
			"""{"rules":{
				"minecraft:entity.arrow.hit_player":{"replacement":"minecraft:block.note_block.harp"},
				"minecraft:block.note_block.harp":{"replacement":"minecraft:entity.arrow.hit_player"}
			}}""".trimIndent()
		)
		SoundManager.install(store(path))

		val played = mutableListOf<SoundInstance>()
		SoundManager.playSubstitute(SoundEvents.NOTE_BLOCK_HARP.value().location(), 0.75f, 1.5f) { instance ->
			played += instance
			assertFalse(SoundManager.applyRule(instance.identifier) { _, _, _ -> })
			assertFalse(SoundManager.applyRule(arrow) { _, _, _ -> })
		}

		assertEquals(1, played.size)
		assertEquals(SoundEvents.NOTE_BLOCK_HARP.value().location(), played.single().identifier)
		assertEquals(0.75f, requested(played.single(), "volume"))
		assertEquals(1.5f, requested(played.single(), "pitch"))
		assertTrue(SoundManager.applyRule(arrow) { _, _, _ -> })
	}

	@Test
	fun `a substitute plays an identifier the sound registry does not hold and is exempt from its own volume rule`() {
		val custom = Identifier.fromNamespaceAndPath("dhen", "custom/airhorn")
		assertNull(BuiltInRegistries.SOUND_EVENT.getValue(custom))

		val played = mutableListOf<SoundInstance>()
		SoundManager.playSubstitute(custom, 1f, 1f) { played += it }

		assertEquals(custom, played.single().identifier)
		assertInstanceOf(SubstituteSound::class.java, played.single())
		assertFalse(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), 1f, 1f) is SubstituteSound)
	}

	@Test
	fun `a removed custom replacement passes the original while a present one dispatches`() {
		val path = directory.resolve("sounds.json")
		val sounds = directory.resolve(CustomSoundPack.DIRECTORY)
		Files.createDirectories(sounds)
		Files.write(sounds.resolve("horn.ogg"), byteArrayOf(1))
		CustomSoundPack.install(directory, CoroutineScope(Dispatchers.Unconfined))
		CustomSoundPack.refresh {}
		SoundManager.install(store(path))
		val original = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		val custom = Dhen.id("custom/horn")
		SoundManager.setReplacement(original, custom, 0.75f, 1.5f)

		assertEquals(Replacement(custom, 0.75f, 1.5f), dispatchedFor(original))

		Files.delete(sounds.resolve("horn.ogg"))
		CustomSoundPack.refresh {}

		var dispatched = 0
		assertFalse(SoundManager.applyRule(original) { _, _, _ -> dispatched++ })
		assertEquals(0, dispatched)
	}

	@Test
	fun `a removed rule is gone from the file and stays gone after a reload`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val orb = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		SoundManager.install(store(path))

		SoundManager.setVolumePercent(orb, 0)
		SoundManager.setReplacement(arrow, SoundEvents.NOTE_BLOCK_HARP.value().location(), 0.75f, 1.5f)
		SoundManager.removeRule(arrow)

		assertFalse(Files.readString(path).contains("arrow"))
		assertFalse(SoundManager.hasRule(arrow))
		assertTrue(SoundManager.hasRule(orb))

		SoundManager.uninstall()
		SoundManager.install(store(path))

		assertFalse(SoundManager.hasRule(arrow))
		assertNull(SoundManager.replacementOf(arrow))
		assertEquals(0, SoundManager.getVolumePercent(orb))
	}

	@Test
	fun `a rule that returns to its defaults leaves the file without disturbing the others`() {
		val path = directory.resolve("sounds.json")
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		val orb = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		SoundManager.install(store(path))

		SoundManager.setReplacement(orb, harp, 0.4f, 1.5f)
		SoundManager.setReplacement(harp, SoundEvents.ARROW_HIT_PLAYER.location(), 1f, 1f)
		SoundManager.setVolumePercent(harp, 40)
		SoundManager.setReplacement(harp, null, 1f, 1f)

		assertTrue(SoundManager.hasRule(harp))
		assertTrue(Files.readString(path).contains("note_block.harp"))

		SoundManager.setVolumePercent(harp, 100)

		assertFalse(SoundManager.hasRule(harp))
		assertFalse(json(Files.readString(path)).getAsJsonObject("rules").has(harp.toString()))

		SoundManager.uninstall()
		SoundManager.install(store(path))

		assertFalse(SoundManager.hasRule(harp))
		assertEquals(Replacement(harp, 0.4f, 1.5f), dispatchedFor(orb))
	}

	@Test
	fun `a replacement written from the editor round trips and lists as a rule`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		SoundManager.install(store(path))

		SoundManager.setReplacement(arrow, harp, 9f, 0.1f)

		assertEquals(harp, SoundManager.replacementOf(arrow))
		assertEquals(1f, SoundManager.replacementVolumeOf(arrow))
		assertEquals(0.5f, SoundManager.replacementPitchOf(arrow))

		SoundManager.uninstall()
		SoundManager.install(store(path))

		assertEquals(listOf(arrow), SoundManager.ruledSounds().map { it.identifier })
		assertEquals(Replacement(harp, 1f, 0.5f), dispatchedFor(arrow))
	}

	@Test
	fun `a substitute never enters recents`() {
		SoundManager.playSubstitute(SoundEvents.NOTE_BLOCK_HARP.value().location(), 1f, 1f) { instance ->
			assertFalse(SoundManager.onSoundPlay(instance))
		}

		assertTrue(SoundManager.recentSounds().isEmpty())
	}

	@Test
	fun `recents de duplicate identifier and pitch pairs cap newest first and reset only on mutation`() {
		val before = SoundManager.recentSoundsVersion
		val identifiers = List(101) { Identifier.fromNamespaceAndPath("test", "sound_$it") }
		for (identifier in identifiers) SoundManager.recordPlayedSound(identifier, 1f)
		SoundManager.recordPlayedSound(identifiers.last(), 1f)
		SoundManager.recordPlayedSound(identifiers.last(), 0.75f)

		val recent = SoundManager.recentSounds()
		assertEquals(100, recent.size)
		assertEquals(RecentSound(identifiers.last(), 0.75f), recent.first())
		assertEquals(RecentSound(identifiers.last(), 1f), recent[1])
		assertEquals(RecentSound(identifiers[2], 1f), recent.last())
		assertEquals(before + 102, SoundManager.recentSoundsVersion)

		SoundManager.clearRecentSounds()
		assertTrue(SoundManager.recentSounds().isEmpty())
		assertEquals(before + 103, SoundManager.recentSoundsVersion)
		SoundManager.clearRecentSounds()
		assertEquals(before + 103, SoundManager.recentSoundsVersion)
	}

	@Test
	fun `manager preview does not enter recents`() {
		SoundManager.playPreview(SoundEvents.NOTE_BLOCK_HARP.value()) { sound ->
			SoundManager.recordPlayedSound(sound.identifier, requested(sound, "pitch"))
		}

		assertTrue(SoundManager.recentSounds().isEmpty())
	}

	@Test
	fun `the manager is opened from the Settings tab and owns no module row`() {
		assertEquals(
			listOf("Open Sound Manager", "Open sounds folder", "Reload custom sounds"),
			ClientPrefs.sections.single { it.title == "Sounds" }.settings.map { it.name }
		)
		assertTrue(ClientPrefs.openSoundsFolder.description.contains(CustomSoundPack.acceptedFormats()))
		assertTrue(ClientPrefs.reloadCustomSounds.description.contains(CustomSoundPack.acceptedFormats()))
	}

	private fun requested(instance: SoundInstance, member: String): Float =
		AbstractSoundInstance::class.java.getDeclaredField(member).apply { isAccessible = true }.getFloat(instance)

	private data class Replacement(val identifier: Identifier, val volume: Float, val pitch: Float)

	private fun dispatchedFor(identifier: Identifier): Replacement? {
		var dispatched: Replacement? = null
		val cancelled = SoundManager.applyRule(identifier) { replacement, volume, pitch ->
			dispatched = Replacement(replacement, volume, pitch)
		}
		assertTrue(cancelled)
		return dispatched
	}

	private fun store(path: Path): ConfigStore = ConfigStore(
		path,
		CoroutineScope(Dispatchers.Unconfined),
		SoundManager.migrations,
		SoundManager.authoritative,
		debounce = {}
	)

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
