package io.github.dzkchen.dhen.features.visual

internal interface WorldClockAccess {
	fun dhenTotalTicks(): Long

	fun dhenPartialTick(): Float

	fun dhenRate(): Float
}
