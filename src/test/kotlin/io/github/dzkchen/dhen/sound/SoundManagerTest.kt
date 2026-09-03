package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.bootstrapMinecraft
import io.github.dzkchen.dhen.json
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
	fun release() = SoundManager.uninstall()

	@Test
	fun `multiplier writes normalize persist and publish to the sound thread`() {
		val path = directory.resolve("sounds.json")
		SoundManager.install(soundStore(path))
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
		SoundManager.install(soundStore(path))
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

		SoundManager.install(soundStore(path))

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

		SoundManager.install(soundStore(path))

		assertEquals(50, SoundManager.getVolumePercent(SoundEvents.NOTE_BLOCK_HARP.value().location()))
		assertEquals(100, SoundManager.getVolumePercent(Identifier.withDefaultNamespace("broken")))
	}

	@Test
	fun `the multipliers migration lifts old volumes into rules and drops the old object`() {
		val path = directory.resolve("sounds.json")
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		Files.writeString(path, """{"multipliers":{"minecraft:block.note_block.harp":0.4}}""")

		SoundManager.install(soundStore(path))
		assertEquals(40, SoundManager.getVolumePercent(harp))

		assertEquals(45, SoundManager.setVolumePercent(harp, 45))
		val written = Files.readString(path)
		assertFalse(written.contains("multipliers"))
		assertTrue(written.contains("rules"))

		SoundManager.uninstall()
		SoundManager.install(soundStore(path))
		assertEquals(45, SoundManager.getVolumePercent(harp))
	}

	@Test
	fun `a rule is only a rule once it differs from the default`() {
		val path = directory.resolve("sounds.json")
		SoundManager.install(soundStore(path))
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
		SoundManager.install(soundStore(path))

		assertTrue(SoundManager.hasRule(arrow))
		assertEquals(Replacement(SoundEvents.NOTE_BLOCK_HARP.value().location(), 0.75f, 1.5f), dispatchedFor(arrow))

		SoundManager.setVolumePercent(arrow, 60)
		SoundManager.uninstall()
		SoundManager.install(soundStore(path))

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
		SoundManager.install(soundStore(path))

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
		SoundManager.install(soundStore(path))

		var dispatched = 0
		assertTrue(SoundManager.applyRule(arrow) { _, _, _ -> dispatched++; true })
		assertEquals(0, dispatched)
		assertFalse(SoundManager.applyRule(harp) { _, _, _ -> dispatched++; true })
		assertEquals(0, dispatched)
	}

	@Test
	fun `a pitch bound rule wins inside its epsilon and the unbound rule handles every other pitch`() {
		val path = directory.resolve("sounds.json")
		val sound = SoundEvents.FLINTANDSTEEL_USE.location()
		val pitch = 0.74603176f
		SoundManager.install(soundStore(path))
		SoundManager.setVolumePercent(sound, 35)
		SoundManager.setVolumePercent(sound, 0, pitch)

		assertEquals(0f, SoundManager.volumeOf(sound, pitch + 0.00005f))
		assertTrue(SoundManager.applyRule(sound, pitch + 0.00005f))
		assertEquals(0.35f, SoundManager.volumeOf(sound, pitch + 0.0002f))
		assertFalse(SoundManager.applyRule(sound, pitch + 0.0002f))

		SoundManager.uninstall()
		SoundManager.install(soundStore(path))

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
		SoundManager.install(soundStore(path))

		val played = mutableListOf<SoundInstance>()
		SoundManager.playSubstitute(SoundEvents.NOTE_BLOCK_HARP.value().location(), 0.75f, 1.5f) { instance ->
			played += instance
			assertFalse(SoundManager.applyRule(instance.identifier) { _, _, _ -> true })
			assertFalse(SoundManager.applyRule(arrow) { _, _, _ -> true })
		}

		assertEquals(1, played.size)
		assertEquals(SoundEvents.NOTE_BLOCK_HARP.value().location(), played.single().identifier)
		assertEquals(0.75f, requestedField(played.single(), "volume"))
		assertEquals(1.5f, requestedField(played.single(), "pitch"))
		assertTrue(SoundManager.applyRule(arrow) { _, _, _ -> true })
	}

	@Test
	fun `a registered substitute is exempt from its own volume rule`() {
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		val played = mutableListOf<SoundInstance>()
		SoundManager.playSubstitute(harp, 1f, 1f) { played += it }

		assertEquals(harp, played.single().identifier)
		assertInstanceOf(SubstituteSound::class.java, played.single())
		assertFalse(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), 1f, 1f) is SubstituteSound)
	}

	@Test
	fun `an unregistered replacement is rejected instead of silencing the original`() {
		val path = directory.resolve("sounds.json")
		SoundManager.install(soundStore(path))
		val original = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		val custom = Identifier.fromNamespaceAndPath("dhen", "custom/horn")
		assertNull(BuiltInRegistries.SOUND_EVENT.getValue(custom))

		SoundManager.setReplacement(original, custom, 0.75f, 1.5f)

		var dispatched = 0
		assertFalse(SoundManager.applyRule(original) { _, _, _ -> dispatched++; true })
		assertEquals(0, dispatched)
		assertNull(SoundManager.replacementOf(original))
		assertFalse(Files.readString(path).contains(custom.toString()))
	}

	@Test
	fun `a persisted unregistered replacement is ignored after custom sound rollback`() {
		val path = directory.resolve("sounds.json")
		val original = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		Files.writeString(
			path,
			"""{"rules":{"$original":{"replacement":"dhen:custom/horn"}}}"""
		)

		SoundManager.install(soundStore(path))

		assertNull(SoundManager.replacementOf(original))
		assertFalse(SoundManager.applyRule(original) { _, _, _ -> true })
	}

	@Test
	fun `a removed rule is gone from the file and stays gone after a reload`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val orb = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		SoundManager.install(soundStore(path))

		SoundManager.setVolumePercent(orb, 0)
		SoundManager.setReplacement(arrow, SoundEvents.NOTE_BLOCK_HARP.value().location(), 0.75f, 1.5f)
		SoundManager.removeRule(arrow)

		assertFalse(Files.readString(path).contains("arrow"))
		assertFalse(SoundManager.hasRule(arrow))
		assertTrue(SoundManager.hasRule(orb))

		SoundManager.uninstall()
		SoundManager.install(soundStore(path))

		assertFalse(SoundManager.hasRule(arrow))
		assertNull(SoundManager.replacementOf(arrow))
		assertEquals(0, SoundManager.getVolumePercent(orb))
	}

	@Test
	fun `a rule that returns to its defaults leaves the file without disturbing the others`() {
		val path = directory.resolve("sounds.json")
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		val orb = SoundEvents.EXPERIENCE_ORB_PICKUP.location()
		SoundManager.install(soundStore(path))

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
		SoundManager.install(soundStore(path))

		assertFalse(SoundManager.hasRule(harp))
		assertEquals(Replacement(harp, 0.4f, 1.5f), dispatchedFor(orb))
	}

	@Test
	fun `a replacement written from the editor round trips and lists as a rule`() {
		val path = directory.resolve("sounds.json")
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		SoundManager.install(soundStore(path))

		SoundManager.setReplacement(arrow, harp, 9f, 0.1f)

		assertEquals(harp, SoundManager.replacementOf(arrow))
		assertEquals(1f, SoundManager.replacementVolumeOf(arrow))
		assertEquals(0.5f, SoundManager.replacementPitchOf(arrow))

		SoundManager.uninstall()
		SoundManager.install(soundStore(path))

		assertEquals(listOf(arrow), SoundManager.ruledSounds().map { it.identifier })
		assertEquals(Replacement(harp, 1f, 0.5f), dispatchedFor(arrow))
	}

	@Test
	fun `a substitute never enters recents`() {
		SoundManager.playSubstitute(SoundEvents.NOTE_BLOCK_HARP.value().location(), 1f, 1f) { instance ->
			assertFalse(SoundManager.onSoundPlay(instance))
		}

		assertTrue(SoundManager.rawRecentSounds().isEmpty())
	}

	@Test
	fun `the recorder keeps repeats apart but suppresses a consecutive near-identical start`() {
		val plays = SoundManager.recordedStarts
		val harp = Identifier.fromNamespaceAndPath("test", "harp")
		val pling = Identifier.fromNamespaceAndPath("test", "pling")
		SoundManager.recordPlayedSound(harp, 1f)
		SoundManager.recordPlayedSound(harp, 1.00001f)
		SoundManager.recordPlayedSound(harp, 0.75f)
		SoundManager.recordPlayedSound(pling, 0.75f)
		SoundManager.recordPlayedSound(harp, 0.75f)

		assertEquals(
			listOf(RecentSound(harp, 0.75f), RecentSound(pling, 0.75f), RecentSound(harp, 0.75f), RecentSound(harp, 1f)),
			SoundManager.rawRecentSounds()
		)
		assertEquals(plays + 4, SoundManager.recordedStarts)
	}

	@Test
	fun `the ring wraps to the newest starts and clearing empties it without rewinding the play count`() {
		val identifiers = List(RECENT_RAW_LIMIT * 3) { Identifier.fromNamespaceAndPath("test", "sound_$it") }
		val plays = SoundManager.recordedStarts
		for (identifier in identifiers) SoundManager.recordPlayedSound(identifier, 1f)

		val raw = SoundManager.rawRecentSounds()
		assertEquals(RECENT_RAW_LIMIT, raw.size)
		assertEquals(RecentSound(identifiers.last(), 1f), raw.first())
		assertEquals(RecentSound(identifiers[identifiers.size - RECENT_RAW_LIMIT], 1f), raw.last())
		assertEquals(plays + identifiers.size, SoundManager.recordedStarts)

		val mark = SoundManager.clearRecentSounds()

		assertEquals(SoundManager.recordedStarts, mark)
		assertTrue(SoundManager.rawRecentSounds().isEmpty())
		assertTrue(SoundManager.recentSnapshot().isEmpty())
		assertEquals(plays + identifiers.size, SoundManager.recordedStarts)
	}

	@Test
	fun `starts since a mark count only what the ring still holds and survive a clear under an open mark`() {
		val mark = SoundManager.recordedStarts

		assertEquals(0, SoundManager.retainedStartsSince(mark))

		for (index in 0 until 3) SoundManager.recordPlayedSound(Identifier.fromNamespaceAndPath("test", "since_$index"), 1f)

		assertEquals(3, SoundManager.retainedStartsSince(mark))

		SoundManager.clearRecentSounds()

		assertEquals(0, SoundManager.retainedStartsSince(mark))

		SoundManager.recordPlayedSound(Identifier.fromNamespaceAndPath("test", "after_clear"), 1f)

		assertEquals(1, SoundManager.retainedStartsSince(mark))

		for (index in 0 until RECENT_RAW_LIMIT * 2) {
			SoundManager.recordPlayedSound(Identifier.fromNamespaceAndPath("test", "flood_$index"), 1f)
		}

		assertEquals(RECENT_RAW_LIMIT, SoundManager.retainedStartsSince(mark))
	}

	@Test
	fun `the snapshot keeps every distinct retained start and folds pitches inside the rule epsilon`() {
		for (index in 0 until 30) SoundManager.recordPlayedSound(Identifier.fromNamespaceAndPath("test", "sound_$index"), 1f)

		assertEquals(30, SoundManager.recentSnapshot().size)

		SoundManager.clearRecentSounds()
		val harp = Identifier.fromNamespaceAndPath("test", "harp")
		val pling = Identifier.fromNamespaceAndPath("test", "pling")
		SoundManager.recordPlayedSound(harp, 0.53968257f)
		SoundManager.recordPlayedSound(pling, 1f)
		SoundManager.recordPlayedSound(harp, 0.5396800f)
		SoundManager.recordPlayedSound(pling, 1f)
		SoundManager.recordPlayedSound(harp, 0.75f)

		assertEquals(
			listOf(RecentSound(harp, 0.75f), RecentSound(pling, 1f), RecentSound(harp, 0.5396800f)),
			SoundManager.recentSnapshot()
		)
	}

	@Test
	fun `a snapshot taken while playback records stays coherent and bounded`() {
		val identifiers = List(RECENT_RAW_LIMIT * 2) { Identifier.fromNamespaceAndPath("test", "overlap_$it") }
		val recorder = Thread {
			for (identifier in identifiers) SoundManager.recordPlayedSound(identifier, 1f)
		}
		recorder.start()
		var reads = 0
		while (recorder.isAlive || reads < 64) {
			val raw = SoundManager.rawRecentSounds()
			assertTrue(raw.size <= RECENT_RAW_LIMIT)
			for (index in 1 until raw.size) {
				assertEquals(suffix(raw[index - 1]) - 1, suffix(raw[index]))
			}
			assertTrue(SoundManager.recentSnapshot().size <= RECENT_RAW_LIMIT)
			reads++
		}
		recorder.join()

		assertEquals(RECENT_RAW_LIMIT, SoundManager.rawRecentSounds().size)
	}

	@Test
	fun `manager preview does not enter recents`() {
		SoundManager.playPreview(SoundEvents.NOTE_BLOCK_HARP.value()) { sound ->
			SoundManager.recordPlayedSound(sound.identifier, requestedField(sound, "pitch"))
		}

		assertTrue(SoundManager.rawRecentSounds().isEmpty())
	}

	private fun suffix(recent: RecentSound): Int = recent.identifier.path.substringAfterLast('_').toInt()

	private data class Replacement(val identifier: Identifier, val volume: Float, val pitch: Float)

	private fun dispatchedFor(identifier: Identifier): Replacement? {
		var dispatched: Replacement? = null
		val cancelled = SoundManager.applyRule(identifier) { replacement, volume, pitch ->
			dispatched = Replacement(replacement, volume, pitch)
			true
		}
		assertTrue(cancelled)
		return dispatched
	}

	private companion object {
		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
