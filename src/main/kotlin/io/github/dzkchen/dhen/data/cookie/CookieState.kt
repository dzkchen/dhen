package io.github.dzkchen.dhen.data.cookie

object CookieState {
	const val UNKNOWN = 0L
	const val EXPIRED = 1L

	var expiry: Long = UNKNOWN
		private set

	internal fun expires(at: Long): Boolean {
		if (expiry == at) return false
		expiry = at
		return true
	}

	internal fun reset(): Boolean = expires(UNKNOWN)
}
