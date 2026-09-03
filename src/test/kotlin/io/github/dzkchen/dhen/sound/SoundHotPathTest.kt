package io.github.dzkchen.dhen.sound

import io.github.dzkchen.dhen.Allocations
import io.github.dzkchen.dhen.bootstrapMinecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

class SoundHotPathTest {
	@TempDir
	lateinit var directory: Path

	@AfterEach
	fun release() = SoundManager.uninstall()

	@Test
	fun `the recorder allocates nothing whether it retains a start or drops a repeat`() {
		assumeTrue(Allocations.measurable)
		val rotation = Array(4) { Identifier.fromNamespaceAndPath("test", "recorded_$it") }
		var next = 0

		assertEquals(0.0, Allocations.bytesPerCall { SoundManager.recordPlayedSound(rotation[next++ and 3], 1f) }, TOLERANCE)
		assertEquals(0.0, Allocations.bytesPerCall { SoundManager.recordPlayedSound(rotation[0], 1f) }, TOLERANCE)
	}

	@Test
	fun `the rule lookup allocates nothing at any rule count`() {
		assumeTrue(Allocations.measurable)
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val absent = Identifier.fromNamespaceAndPath("test", "absent")

		for (count in RULE_COUNTS) {
			install(rules(count, arrow, replaced = false))
			assertEquals(0.0, Allocations.bytesPerCall { Allocations.floatSink = SoundManager.volumeOf(arrow, 1f) }, TOLERANCE)
			assertEquals(0.0, Allocations.bytesPerCall { Allocations.floatSink = SoundManager.volumeOf(absent, 1f) }, TOLERANCE)
			assertEquals(
				0.0,
				Allocations.bytesPerCall { Allocations.flagSink = SoundManager.applyRule(arrow, 1f, ACCEPT) },
				TOLERANCE
			)
			assertEquals(
				0.0,
				Allocations.bytesPerCall { Allocations.flagSink = SoundManager.applyRule(absent, 1f, ACCEPT) },
				TOLERANCE
			)
			SoundManager.uninstall()
		}
	}

	@Test
	fun `a matched replacement allocates one substitute sound above the executor floor`() {
		assumeTrue(Allocations.measurable)
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		val substitute = Allocations.bytesPerCall { Allocations.sink = SubstituteSound(harp, 1f, 1f) }
		val deferral = Allocations.bytesPerCall { pending.add(TOKEN); Allocations.sink = pending.poll() }
		assertTrue(substitute > 0.0)
		assertTrue(deferral > 0.0)

		for (count in RULE_COUNTS.drop(1)) {
			install(rules(count, arrow, replaced = true))
			val inline = Allocations.bytesPerCall {
				SoundManager.applyRule(arrow, 1f, dispatchUnscheduled)
				SoundManager.playPendingReplacement(DISCARD)
			}
			val deferred = Allocations.bytesPerCall {
				SoundManager.applyRule(arrow, 1f, dispatchDeferred)
				Allocations.sink = pending.poll()
				SoundManager.playPendingReplacement(DISCARD)
			}
			assertEquals(substitute, inline, TOLERANCE)
			assertEquals(substitute + deferral, deferred, TOLERANCE)
			SoundManager.uninstall()
		}
	}

	@Test
	fun `dispatch schedules one task per accepted replacement and none for a rejected one`() {
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		var scheduled = 0
		val count: (Runnable) -> Unit = { scheduled++ }

		for (slot in 0 until PENDING_REPLACEMENT_LIMIT) {
			assertTrue(SoundManager.dispatchReplacement(harp, 1f, 1f, count))
		}
		assertEquals(PENDING_REPLACEMENT_LIMIT, scheduled)

		assertFalse(SoundManager.dispatchReplacement(harp, 1f, 1f, count))
		assertEquals(PENDING_REPLACEMENT_LIMIT, scheduled)
	}

	@Test
	fun `a replacement dispatched under an audition plays at preview volume`() {
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		val played = mutableListOf<SoundInstance>()
		val collect: (SoundInstance) -> Unit = { played += it }
		val ignore: (Runnable) -> Unit = {}

		SoundManager.dispatchReplacement(harp, 1f, 1f, ignore)
		SoundManager.playPreview(SoundEvents.NOTE_BLOCK_HARP.value()) {
			SoundManager.dispatchReplacement(harp, 1f, 1f, ignore)
		}

		assertTrue(SoundManager.playPendingReplacement(collect))
		assertTrue(SoundManager.playPendingReplacement(collect))
		assertEquals(1f, requestedField(played.first(), "volume"))
		assertEquals(0.25f, requestedField(played.last(), "volume"))
	}

	@Test
	fun `a full queue plays the original instead of cancelling it into silence`() {
		val arrow = SoundEvents.ARROW_HIT_PLAYER.location()
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		install("""{"rules":{"$arrow":{"replacement":"$harp"}}}""")

		for (slot in 0 until PENDING_REPLACEMENT_LIMIT) assertTrue(SoundManager.offerReplacement(harp, 1f, 1f))
		assertFalse(SoundManager.offerReplacement(harp, 1f, 1f))
		assertFalse(SoundManager.applyRule(arrow, 1f, OFFER))

		assertTrue(SoundManager.playPendingReplacement(DISCARD))
		assertTrue(SoundManager.applyRule(arrow, 1f, OFFER))
	}

	@Test
	fun `teardown drops pending work so nothing plays after it`() {
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		assertTrue(SoundManager.offerReplacement(harp, 1f, 1f))
		assertTrue(SoundManager.offerReplacement(harp, 1f, 1f))

		SoundManager.forgetPlayback()

		var played: SoundInstance? = null
		assertFalse(SoundManager.playPendingReplacement { played = it })
		assertNull(played)
		assertTrue(SoundManager.offerReplacement(harp, 1f, 1f))
		assertTrue(SoundManager.playPendingReplacement { played = it })
		assertEquals(harp, played?.identifier)
	}

	@Test
	fun `a substitute drained inside another substitute stays independent and ordered`() {
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		val pling = SoundEvents.NOTE_BLOCK_PLING.value().location()
		install("""{"rules":{"$harp":{"replacement":"$pling"},"$pling":{"replacement":"$harp"}}}""")
		assertTrue(SoundManager.offerReplacement(harp, 1f, 1f))
		assertTrue(SoundManager.offerReplacement(pling, 1f, 1f))

		val played = mutableListOf<SoundInstance>()
		SoundManager.playPendingReplacement { outer ->
			played += outer
			assertFalse(SoundManager.applyRule(outer.identifier, dispatch = ACCEPT))
			SoundManager.playPendingReplacement { inner ->
				played += inner
				assertFalse(SoundManager.applyRule(inner.identifier, dispatch = ACCEPT))
			}
			assertFalse(SoundManager.applyRule(outer.identifier, dispatch = ACCEPT))
		}

		assertEquals(listOf(harp, pling), played.map { it.identifier })
		assertEquals(2, played.distinct().size)
		assertFalse(SoundManager.playPendingReplacement(DISCARD))
		assertTrue(SoundManager.applyRule(harp, dispatch = ACCEPT))
	}

	@Test
	fun `offers from another thread are drained once, in ring order, at the volume they carried`() {
		val identifiers = List(PENDING_REPLACEMENT_LIMIT) { Identifier.fromNamespaceAndPath("test", "offthread_$it") }
		val ready = CountDownLatch(1)
		val offering = Thread {
			ready.countDown()
			for (identifier in identifiers) SoundManager.offerReplacement(identifier, 0.75f, 1.5f)
		}
		offering.start()
		ready.await()
		offering.join()

		val drained = mutableListOf<SoundInstance>()
		val collect: (SoundInstance) -> Unit = { drained += it }
		while (SoundManager.playPendingReplacement(collect)) assertTrue(drained.isNotEmpty())

		assertEquals(identifiers, drained.map { it.identifier })
		assertEquals(0.75f, requestedField(drained.first(), "volume"))
		assertEquals(1.5f, requestedField(drained.first(), "pitch"))
	}

	private fun install(document: String) {
		val path = directory.resolve("sounds-${counter++}.json")
		Files.writeString(path, document)
		SoundManager.install(soundStore(path))
	}

	private fun rules(count: Int, matched: Identifier, replaced: Boolean): String {
		if (count == 0) return """{"rules":{}}"""
		val harp = SoundEvents.NOTE_BLOCK_HARP.value().location()
		val body = if (replaced) """{"replacement":"$harp"}""" else """{"volume":0.65}"""
		val filler = (1 until count).joinToString("") { ""","test:filler_$it":$body""" }
		return """{"rules":{"$matched":$body$filler}}"""
	}

	private var counter = 0
	private val pending = ConcurrentLinkedQueue<Runnable>()
	private val unscheduled: (Runnable) -> Unit = {}
	private val deferring: (Runnable) -> Unit = { pending.add(it) }
	private val dispatchUnscheduled = SoundManager.ReplacementDispatch { replacement, volume, pitch ->
		SoundManager.dispatchReplacement(replacement, volume, pitch, unscheduled)
	}
	private val dispatchDeferred = SoundManager.ReplacementDispatch { replacement, volume, pitch ->
		SoundManager.dispatchReplacement(replacement, volume, pitch, deferring)
	}

	private companion object {
		private const val TOLERANCE = 1.0
		private val RULE_COUNTS = listOf(0, 1, 8, 32)
		private val TOKEN = Runnable {}
		private val ACCEPT = SoundManager.ReplacementDispatch { _, _, _ -> true }
		private val OFFER = SoundManager.ReplacementDispatch(SoundManager::offerReplacement)
		private val DISCARD: (SoundInstance) -> Unit = { Allocations.sink = it }

		@JvmStatic
		@BeforeAll
		fun bootstrap() = bootstrapMinecraft()
	}
}
