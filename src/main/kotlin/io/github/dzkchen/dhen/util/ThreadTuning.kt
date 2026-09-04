package io.github.dzkchen.dhen.util

internal object ThreadTuning {
	const val VANILLA = Thread.NORM_PRIORITY

	@Volatile
	private var io = VANILLA

	@JvmStatic
	fun ioPriority(): Int = io

	fun publishIo(priority: Int) {
		io = priority
	}
}
