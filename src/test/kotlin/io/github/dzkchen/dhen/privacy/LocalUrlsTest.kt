package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.gui.Notifications
import io.github.dzkchen.dhen.util.NanoClock
import io.github.dzkchen.dhen.util.TickClock
import kotlinx.coroutines.Dispatchers
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executor

private const val PUBLIC = "http://93.184.216.34"
private const val LOOPBACK = "http://127.0.0.1:8080"
private const val REDIRECT_PROPERTY = "http.maxRedirects"

private class Hop(url: String, private val status: Int, private val location: String? = null) : HttpURLConnection(URL(url)) {
	var follows = true
	val sent = mutableMapOf<String, String>()
	var authenticators = 0

	override fun connect() = Unit

	override fun disconnect() = Unit

	override fun usingProxy() = false

	override fun getResponseCode(): Int = status

	override fun getHeaderField(name: String): String? = if (name == "Location") location else null

	override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

	override fun setInstanceFollowRedirects(followRedirects: Boolean) {
		follows = followRedirects
	}

	override fun setRequestProperty(key: String, value: String?) {
		if (value != null) sent[key] = value
	}

	override fun setAuthenticator(auth: Authenticator) {
		authenticators++
	}
}

class LocalUrlsTest {
	private val bus = EventBus()
	private val chat = mutableListOf<Component>()
	private val opened = mutableListOf<URL>()
	private val proxies = mutableListOf<Proxy>()
	private val headers = mapOf("X-Minecraft-Username" to "tester")

	@BeforeEach
	@AfterEach
	fun reset() {
		LocalUrls.uninstall()
		chat.clear()
		opened.clear()
		proxies.clear()
		Notifications.clear()
		ClientPrefs.blockLocalUrls.reset()
		ClientPrefs.chatAlerts.reset()
		ClientPrefs.toastPopups.reset()
		ClientPrefs.logEvents.reset()
		PrivacyLog.install(bus, Executor(Runnable::run), chat::add, NanoClock { 0L })
		PrivacyLog.clearCooldowns()
		PortScans.reset()
		LocalUrls.serverConnected(null)
		System.clearProperty(REDIRECT_PROPERTY)
	}

	private fun chain(vararg hops: Hop): LocalUrls.Opener {
		val remaining = hops.toMutableList()
		return LocalUrls.Opener { url, proxy ->
			opened += url
			proxies += proxy
			remaining.removeFirst()
		}
	}

	private fun follow(first: Hop, opener: LocalUrls.Opener, proxy: Proxy = Proxy.NO_PROXY) =
		LocalUrls.follow(first, proxy, headers, opener)

	@Test
	fun `a redirect chain that lands on loopback is refused and tallied`() {
		val first = Hop("$PUBLIC/pack.zip", 302, "$LOOPBACK/pack.zip")
		assertThrows(IllegalStateException::class.java) { follow(first, chain()) }
		assertTrue(chat.map { it.string }.any { it.endsWith("Port scan blocked: 127.0.0.1:8080") }, chat.toString())
		assertTrue(opened.isEmpty())
	}

	@Test
	fun `a first hop already on loopback is refused before any request`() {
		val first = Hop("$LOOPBACK/pack.zip", 200)
		assertThrows(IllegalStateException::class.java) { follow(first, chain()) }
	}

	@Test
	fun `a chain to a public host is followed to its last hop`() {
		val first = Hop("$PUBLIC/pack.zip", 302, "$PUBLIC/moved/pack.zip")
		val last = Hop("$PUBLIC/moved/pack.zip", 200)
		assertSame(last, follow(first, chain(last)))
		assertFalse(first.follows)
		assertFalse(last.follows)
		assertEquals(headers, last.sent)
		assertEquals(1, last.authenticators)
	}

	@Test
	fun `a relative location is resolved against the hop it came from`() {
		val first = Hop("$PUBLIC/packs/pack.zip", 302, "/elsewhere/pack.zip")
		val last = Hop("$PUBLIC/elsewhere/pack.zip", 200)
		follow(first, chain(last))
		assertEquals(listOf(URL("$PUBLIC/elsewhere/pack.zip")), opened)
	}

	@Test
	fun `a cross-protocol hop is not followed`() {
		val first = Hop("$PUBLIC/pack.zip", 302, "https://93.184.216.34/pack.zip")
		assertSame(first, follow(first, chain()))
		assertTrue(opened.isEmpty())
	}

	@Test
	fun `a use-proxy hop routes the original url through the named proxy`() {
		val first = Hop("$PUBLIC/pack.zip", 305, "$PUBLIC:3128/")
		val last = Hop("$PUBLIC/pack.zip", 200)
		assertSame(last, follow(first, chain(last)))
		assertEquals(listOf(URL("$PUBLIC/pack.zip")), opened)
		val hop = proxies.single()
		assertEquals(Proxy.Type.HTTP, hop.type())
		assertEquals("93.184.216.34:3128", (hop.address() as InetSocketAddress).let { "${it.hostString}:${it.port}" })
	}

	@Test
	fun `a use-proxy hop pointing at loopback is refused`() {
		val first = Hop("$PUBLIC/pack.zip", 305, "$LOOPBACK/")
		assertThrows(IllegalStateException::class.java) { follow(first, chain()) }
	}

	@Test
	fun `the redirect cap stops the chain the way vanilla does`() {
		System.setProperty(REDIRECT_PROPERTY, "1")
		val first = Hop("$PUBLIC/pack.zip", 302, "zzz://93.184.216.34/deep")
		val failure = assertThrows(ProtocolException::class.java) { follow(first, chain()) }
		assertEquals("Server redirected too many times (1)", failure.message)
		assertTrue(opened.isEmpty())
	}

	@Test
	fun `a status with a location that is not a redirect ends the chain`() {
		val first = Hop("$PUBLIC/pack.zip", 200, "$LOOPBACK/pack.zip")
		assertSame(first, follow(first, chain()))
	}

	@Test
	fun `a server that is itself local exempts a local download`() {
		LocalUrls.serverConnected(InetSocketAddress("192.168.1.5", 25565))
		assertTrue(LocalUrls.serverIsLocal())
		val first = Hop("$LOOPBACK/pack.zip", 200)
		assertSame(first, follow(first, chain()))
		assertEquals(emptyList<Component>(), chat)
	}

	@Test
	fun `an unresolved server address leaves the guard armed`() {
		LocalUrls.serverConnected(InetSocketAddress.createUnresolved("192.168.1.5", 25565))
		assertFalse(LocalUrls.serverIsLocal())
	}

	@Test
	fun `joining schedules one summary two seconds later`() {
		LocalUrls.install(bus, Dispatchers.Unconfined)
		PortScans.detected("$LOOPBACK/pack.zip")
		chat.clear()
		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.JOIN))
		repeat(LocalUrls.SUMMARY_DELAY_TICKS - 1) { TickClock.clientTicked() }
		assertEquals(emptyList<Component>(), chat)
		TickClock.clientTicked()
		assertEquals(1, chat.size)
		assertTrue(chat.single().string.contains("Detected 1 local port scan to 127.0.0.1:8080"), chat.toString())
	}

	@Test
	fun `disconnecting forgets the tally and the pending summary`() {
		LocalUrls.install(bus, Dispatchers.Unconfined)
		LocalUrls.serverConnected(InetSocketAddress("192.168.1.5", 25565))
		PortScans.detected("$LOOPBACK/pack.zip")
		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.JOIN))
		bus.type<WorldChangeEvent>().dispatch(WorldChangeEvent(WorldChange.DISCONNECT))
		assertFalse(LocalUrls.serverIsLocal())
		chat.clear()
		repeat(LocalUrls.SUMMARY_DELAY_TICKS) { TickClock.clientTicked() }
		assertEquals(emptyList<Component>(), chat)
	}

	@Test
	fun `the switch gates the whole guard`() {
		LocalUrls.install(bus, Dispatchers.Unconfined)
		assertTrue(LocalUrls.guarding())
		ClientPrefs.blockLocalUrls.on = false
		assertFalse(LocalUrls.guarding())
		ClientPrefs.blockLocalUrls.on = true
		LocalUrls.uninstall()
		assertFalse(LocalUrls.guarding())
	}
}
