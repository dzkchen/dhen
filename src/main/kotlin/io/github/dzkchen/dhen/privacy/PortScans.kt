package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException

object PortScans {
	const val MAX_PENDING = 100
	const val MAX_LISTED = 5
	const val ALERT_COOLDOWN_MS = 5000L

	private const val COOLDOWN_KEY = "localpack_alert"
	private const val CATEGORY = "LocalPack"
	private const val SUMMARY_LOG = "Port scan summary: {} blocked requests to {} unique targets"
	private const val TOAST_TITLE = "Local URL Scan Detected"
	private const val HTTPS = "https"
	private const val HTTPS_PORT = 443
	private const val HTTP_PORT = 80
	private const val UNGUARDED_PORT = 0

	private val SCHEME = Regex("^https?://")

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
	private val lock = Any()
	private val pending = LinkedHashSet<String>()
	private var total = 0
	private var summarised = false

	fun detected(url: String) {
		val uri = parse(url)
		if (uri?.port == UNGUARDED_PORT) return
		val target = target(url, uri)
		PrivacyLog.logDetection(CATEGORY, "Blocked local URL probe: $url")
		val speak = !PrivacyLog.onCooldown(COOLDOWN_KEY, ALERT_COOLDOWN_MS)
		synchronized(lock) {
			total++
			if (pending.size < MAX_PENDING) pending += target
		}
		if (!speak) return
		PrivacyLog.alert(PrivacyLog.Alert.DANGER, "Port scan blocked: $target")
		PrivacyLog.toast(PrivacyLog.Alert.DANGER, TOAST_TITLE)
	}

	fun summarise() {
		val line = synchronized(lock) {
			if (summarised || pending.isEmpty()) return
			summarised = true
			log.info(SUMMARY_LOG, total, pending.size)
			summary(total, pending)
		}
		PrivacyLog.alert(PrivacyLog.Alert.DANGER, line)
	}

	fun reset() {
		synchronized(lock) {
			pending.clear()
			total = 0
			summarised = false
		}
	}

	private fun summary(count: Int, targets: Set<String>): String {
		val scans = if (count == 1) "scan" else "scans"
		if (targets.size == 1) return "Detected $count local port $scans to ${targets.first()}"
		val hidden = targets.size - MAX_LISTED
		val listed = targets.asSequence().take(MAX_LISTED).joinToString(", ")
		val more = if (hidden > 0) " +$hidden more" else ""
		return "Detected $count local port $scans: $listed$more"
	}

	private fun target(url: String, uri: URI?): String {
		if (uri == null) return stripped(url)
		val host = uri.host ?: return url
		val port = if (uri.port != -1) uri.port else if (uri.scheme == HTTPS) HTTPS_PORT else HTTP_PORT
		return "$host:$port"
	}

	private fun parse(url: String): URI? = try {
		URI(url)
	} catch (_: URISyntaxException) {
		null
	}

	private fun stripped(url: String): String {
		val authority = url.replaceFirst(SCHEME, "")
		val slash = authority.indexOf('/')
		val cut = if (slash > 0) authority.substring(0, slash) else authority
		return cut.ifEmpty { url }
	}
}
