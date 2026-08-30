package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.util.NanoClock
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor

class PortScansTest {
	private val bus = EventBus()
	private val chat = mutableListOf<Component>()
	private var nanos = 0L

	@BeforeEach
	@AfterEach
	fun reset() {
		chat.clear()
		nanos = 0L
		Notifications.clear()
		ClientPrefs.chatAlerts.reset()
		ClientPrefs.toastPopups.reset()
		ClientPrefs.logEvents.reset()
		PrivacyLog.install(bus, Executor(Runnable::run), chat::add, NanoClock { nanos })
		PrivacyLog.clearCooldowns()
		PortScans.reset()
	}

	private fun lines() = chat.map { it.string }

	private fun advancePastCooldown() {
		nanos += (PortScans.ALERT_COOLDOWN_MS + 1) * 1_000_000L
	}

	@Test
	fun `a probe names the host and its default port`() {
		PortScans.detected("http://127.0.0.1/pack.zip")
		assertTrue(lines().any { it.endsWith("Port scan blocked: 127.0.0.1:80") }, lines().toString())
	}

	@Test
	fun `an https probe defaults to 443 and an explicit port wins`() {
		PortScans.detected("https://127.0.0.1/pack.zip")
		advancePastCooldown()
		PortScans.detected("http://127.0.0.1:8080/pack.zip")
		PortScans.summarise()
		assertTrue(lines().last().contains("127.0.0.1:443"), lines().toString())
		assertTrue(lines().last().contains("127.0.0.1:8080"), lines().toString())
	}

	@Test
	fun `a url the parser rejects falls back to its stripped authority`() {
		PortScans.detected("http://12 7.0.0.1:8080/pack.zip")
		assertTrue(lines().any { it.endsWith("Port scan blocked: 12 7.0.0.1:8080") }, lines().toString())
	}

	@Test
	fun `port zero is skipped before any bookkeeping`() {
		PortScans.detected("http://127.0.0.1:0/probe")
		PortScans.summarise()
		assertEquals(emptyList<String>(), lines())
	}

	@Test
	fun `the alert and the toast share one cooldown`() {
		PortScans.detected("http://127.0.0.1:1/a")
		val first = chat.size
		PortScans.detected("http://127.0.0.1:2/b")
		assertEquals(first, chat.size)
		advancePastCooldown()
		PortScans.detected("http://127.0.0.1:3/c")
		assertTrue(chat.size > first)
	}

	@Test
	fun `the pending set stops at its cap while the total keeps counting`() {
		for (port in 1..PortScans.MAX_PENDING + 20) PortScans.detected("http://127.0.0.1:$port/pack.zip")
		chat.clear()
		PortScans.summarise()
		val summary = lines().single()
		assertTrue(summary.contains("Detected ${PortScans.MAX_PENDING + 20} local port scans"), summary)
		assertTrue(summary.endsWith("+${PortScans.MAX_PENDING - PortScans.MAX_LISTED} more"), summary)
	}

	@Test
	fun `a summary lists five targets and counts the rest`() {
		for (port in 1..PortScans.MAX_LISTED + 2) PortScans.detected("http://127.0.0.1:$port/pack.zip")
		chat.clear()
		PortScans.summarise()
		val summary = lines().single()
		assertTrue(summary.contains("127.0.0.1:1, 127.0.0.1:2, 127.0.0.1:3, 127.0.0.1:4, 127.0.0.1:5 +2 more"), summary)
	}

	@Test
	fun `a single target reads as one scan without a list`() {
		PortScans.detected("http://127.0.0.1:8080/pack.zip")
		chat.clear()
		PortScans.summarise()
		assertEquals("⛔ Detected 1 local port scan to 127.0.0.1:8080", lines().single())
	}

	@Test
	fun `the summary is emitted once and never on an empty tally`() {
		PortScans.summarise()
		assertEquals(emptyList<String>(), lines())
		PortScans.detected("http://127.0.0.1:8080/pack.zip")
		chat.clear()
		PortScans.summarise()
		PortScans.summarise()
		assertEquals(1, chat.size)
	}

	@Test
	fun `resetting forgets the tally so the next connection starts clean`() {
		PortScans.detected("http://127.0.0.1:8080/pack.zip")
		PortScans.reset()
		chat.clear()
		PortScans.summarise()
		assertEquals(emptyList<String>(), lines())
	}
}
