package io.github.dzkchen.dhen.privacy

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal object ShaderStripTracker {
	internal class Token {
		internal val pending = ConcurrentHashMap<String, Paths>()
		internal val announced = ConcurrentHashMap.newKeySet<String>()
	}

	internal class Paths(first: String) {
		private val size = AtomicInteger(1)
		val values: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().also { it += first }

		fun add(path: String) {
			if (path in values) return
			while (true) {
				val current = size.get()
				if (current >= MAX_TRACKED_PATHS) return
				if (!size.compareAndSet(current, current + 1)) continue
				if (!values.add(path)) size.decrementAndGet()
				return
			}
		}
	}

	private class Announcement(val namespace: String, val paths: List<String>)

	@Volatile
	private var current = Token()

	fun onStripped(namespace: String, path: String) {
		onStripped(current, namespace, path)
	}

	fun onStripped(token: Token, namespace: String, path: String) {
		if (token !== current || namespace in token.announced) return
		val existing = token.pending[namespace]
		if (existing != null) {
			existing.add(path)
			return
		}
		val created = Paths(path)
		token.pending.putIfAbsent(namespace, created)?.add(path)
	}

	fun flushPending(playerPresent: Boolean) {
		val token = current
		if (!playerPresent || token.pending.isEmpty()) return
		val batch = ArrayList<Announcement>()
		for ((namespace, paths) in token.pending) {
			if (!token.pending.remove(namespace, paths) || !token.announced.add(namespace)) continue
			batch += Announcement(namespace, paths.values.sorted())
		}
		if (batch.isEmpty() || token !== current) return
		batch.sortBy(Announcement::namespace)
		for (entry in batch) {
			PrivacyLog.logDetection(
				CATEGORY,
				"Stripped ${entry.paths.size} shader override(s) for '${entry.namespace}': ${entry.paths}"
			)
		}
		val message = if (batch.size == 1) {
			val entry = batch[0]
			"Stripped ${entry.paths.size} server-pack shader override(s) targeting '${entry.namespace}'"
		} else {
			"Stripped server-pack shader overrides targeting ${batch.size} mods: " +
				batch.joinToString { it.namespace }
		}
		PrivacyLog.alert(PrivacyLog.Alert.DANGER, message)
		PrivacyLog.toastWithCooldown(PrivacyLog.Alert.DANGER, TOAST, TOAST_KEY, TOAST_COOLDOWN_MS)
	}

	fun clear() {
		current = Token()
	}

	fun token(): Token = current

	internal const val MAX_TRACKED_PATHS = 64
	private const val CATEGORY = "ShaderStrip"
	private const val TOAST = "Malicious Shader Override Stripped"
	private const val TOAST_KEY = "shader_strip_toast"
	private const val TOAST_COOLDOWN_MS = 5_000L
}
