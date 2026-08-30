package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.event.EventBus
import io.github.dzkchen.dhen.event.Handle
import io.github.dzkchen.dhen.event.WorldChange
import io.github.dzkchen.dhen.event.WorldChangeEvent
import io.github.dzkchen.dhen.gui.ClientPrefs
import io.github.dzkchen.dhen.util.delayTicks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.net.URL

object LocalUrls {
	fun interface Opener {
		fun open(url: URL, proxy: Proxy): HttpURLConnection
	}

	const val SUMMARY_DELAY_TICKS = 40
	const val DEFAULT_MAX_REDIRECTS = 20

	private const val MAX_REDIRECTS_PROPERTY = "http.maxRedirects"
	private const val LOCATION = "Location"
	private const val USE_PROXY = 305
	private const val HTTP = "http"
	private const val HTTPS = "https"
	private const val REFUSED = "Tried to connect to a local address"
	private const val REFUSED_FORMAT = "Blocked a server download aimed at the local address {}"

	private val SYSTEM = Opener { url, proxy -> url.openConnection(proxy) as HttpURLConnection }

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	@Volatile
	private var installed = false

	@Volatile
	private var serverAddress: InetAddress? = null

	private var subscription: Handle? = null
	private var scope: CoroutineScope? = null
	private var summary: Job? = null

	fun install(bus: EventBus, dispatcher: CoroutineDispatcher) {
		uninstall()
		scope = CoroutineScope(SupervisorJob() + dispatcher)
		subscription = bus.subscribe<WorldChangeEvent> {
			when (it.phase) {
				WorldChange.JOIN -> scheduleSummary()
				WorldChange.DISCONNECT -> disconnected()
				else -> Unit
			}
		}
		installed = true
	}

	fun uninstall() {
		installed = false
		subscription?.unsubscribe()
		subscription = null
		scope?.cancel()
		scope = null
		disconnected()
	}

	@JvmStatic
	fun guarding(): Boolean = installed && ClientPrefs.blockLocalUrls.on

	@JvmStatic
	fun serverConnected(address: SocketAddress?) {
		serverAddress = (address as? InetSocketAddress)?.address
	}

	@JvmStatic
	fun follow(first: HttpURLConnection, proxy: Proxy, headers: Map<String, String>): HttpURLConnection =
		follow(first, proxy, headers, SYSTEM)

	internal fun follow(
		first: HttpURLConnection,
		proxy: Proxy,
		headers: Map<String, String>,
		open: Opener
	): HttpURLConnection {
		var connection = first
		connection.instanceFollowRedirects = false
		refuse(connection.url)
		val cap = maxRedirects()
		var redirects = 0
		var status = connection.responseCode
		while (isRedirect(status)) {
			val location = connection.getHeaderField(LOCATION) ?: break
			if (redirects >= cap - 1) {
				probeLikeVanilla(connection, location)
				throw ProtocolException("Server redirected too many times ($cap)")
			}
			val hop = if (status == USE_PROXY) viaProxy(connection, location, open)
			else nextHop(connection, location, proxy, open)
			connection = hop ?: break
			connection.setAuthenticator(object : Authenticator() {})
			connection.instanceFollowRedirects = false
			headers.forEach(connection::setRequestProperty)
			status = connection.responseCode
			redirects++
		}
		return connection
	}

	internal fun serverIsLocal(): Boolean = serverAddress?.let { LocalAddresses.isLocal(it) } == true

	private fun viaProxy(connection: HttpURLConnection, location: String, open: Opener): HttpURLConnection? {
		val target = absoluteOrNull(location) ?: relativeOrNull(connection.url, location) ?: return null
		if (!target.protocol.equals(HTTP, ignoreCase = true) && !target.protocol.equals(HTTPS, ignoreCase = true)) {
			return null
		}
		refuse(target)
		val hop = InetSocketAddress.createUnresolved(target.host, portOf(target))
		return open.open(connection.url, Proxy(Proxy.Type.HTTP, hop))
	}

	private fun nextHop(connection: HttpURLConnection, location: String, proxy: Proxy, open: Opener): HttpURLConnection? {
		val absolute = absoluteOrNull(location)
		if (absolute != null && !connection.url.protocol.equals(absolute.protocol, ignoreCase = true)) return null
		val target = absolute ?: URL(connection.url, location)
		refuse(target)
		return open.open(target, proxy)
	}

	private fun refuse(url: URL) {
		if (serverIsLocal() || !LocalAddresses.isLocal(url.host)) return
		log.warn(REFUSED_FORMAT, url)
		PortScans.detected(url.toString())
		throw IllegalStateException(REFUSED)
	}

	private fun probeLikeVanilla(connection: HttpURLConnection, location: String) {
		try {
			val target = absoluteOrNull(location) ?: URL(connection.url, location)
			Socket(target.host, portOf(target))
		} catch (_: Exception) {
		}
	}

	private fun scheduleSummary() {
		summary?.cancel()
		summary = scope?.launch {
			delayTicks(SUMMARY_DELAY_TICKS)
			PortScans.summarise()
		}
	}

	private fun disconnected() {
		summary?.cancel()
		summary = null
		serverAddress = null
		PortScans.reset()
	}

	private fun maxRedirects(): Int =
		System.getProperty(MAX_REDIRECTS_PROPERTY)?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_MAX_REDIRECTS

	private fun isRedirect(status: Int): Boolean = when (status) {
		300, 301, 302, 303, USE_PROXY, 307 -> true
		else -> false
	}

	private fun portOf(url: URL): Int = if (url.port == -1) url.defaultPort else url.port

	private fun absoluteOrNull(location: String): URL? = try {
		URL(location)
	} catch (_: MalformedURLException) {
		null
	}

	private fun relativeOrNull(base: URL, location: String): URL? = try {
		URL(base, location)
	} catch (_: MalformedURLException) {
		null
	}
}
