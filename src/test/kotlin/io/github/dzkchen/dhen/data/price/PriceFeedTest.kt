package io.github.dzkchen.dhen.data.price

import io.github.dzkchen.dhen.util.WebSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class PriceFeedTest {
	private val source = FakeSource()
	private val feed =
		PriceFeed("test", "https://example.invalid/prices", 5.minutes, Map<String, Double>::size, PriceTables::lowestBins)

	@Test
	fun `a feed is stale until its first successful fetch`() {
		assertTrue(feed.stale(FIRST))

		refresh(FIRST)

		assertFalse(feed.stale(FIRST))
		assertEquals(1.0, feed.value?.get("HYPERION"))
	}

	@Test
	fun `a feed stays fresh until its ttl expires and goes stale after`() {
		refresh(FIRST)

		assertFalse(feed.stale(FIRST + TTL - 1))
		assertTrue(feed.stale(FIRST + TTL))
	}

	@Test
	fun `an unreachable source keeps the last prices and its timestamp, and counts the failure`() {
		refresh(FIRST)
		source.reachable = false

		assertFalse(refresh(FIRST + TTL))

		assertEquals(1.0, feed.value?.get("HYPERION"))
		assertEquals(FIRST, feed.updatedAt)
		assertEquals(1, feed.failures)
		assertTrue(feed.stale(FIRST + TTL))
	}

	@Test
	fun `an unreadable body keeps the last prices rather than throwing`() {
		refresh(FIRST)
		source.body = "not json at all"

		assertFalse(refresh(FIRST + TTL))

		assertEquals(1.0, feed.value?.get("HYPERION"))
		assertEquals(1, feed.failures)
	}

	@Test
	fun `a reply that parses to nothing is a failed refresh, not an empty price list`() {
		refresh(FIRST)
		source.body = "{}"

		assertFalse(refresh(FIRST + TTL))

		assertEquals(1.0, feed.value?.get("HYPERION"))
		assertEquals(FIRST, feed.updatedAt)
		assertEquals(1, feed.failures)
	}

	@Test
	fun `a successful refresh clears the failure count`() {
		source.reachable = false
		refresh(FIRST)
		source.reachable = true

		assertTrue(refresh(FIRST + TTL))

		assertEquals(0, feed.failures)
		assertEquals(1, feed.size)
	}

	@Test
	fun `a refresh nobody wants any more is thrown away rather than written back`() {
		var wanted = true
		feed.refresh(source, FIRST) { wanted }
		wanted = false

		assertFalse(feed.refresh(source, FIRST + TTL) { wanted })

		assertEquals(FIRST, feed.updatedAt)
		assertEquals(0, feed.failures)
	}

	private fun refresh(now: Long): Boolean = feed.refresh(source, now) { true }

	private class FakeSource : WebSource {
		var reachable = true
		var body = "{\"HYPERION\":1.0}"

		override fun text(url: String): String? = if (reachable) body else null
	}

	private companion object {
		private val TTL = 5.minutes.inWholeNanoseconds
		private val FIRST = 7.minutes.inWholeNanoseconds
	}
}
