package io.github.dzkchen.dhen.features.qol

import io.github.dzkchen.dhen.Dhen
import io.github.dzkchen.dhen.module.Category
import io.github.dzkchen.dhen.module.Module
import net.minecraft.network.protocol.Packet
import org.slf4j.LoggerFactory

object NetworkResilience : Module(
	name = "Network Resilience",
	category = Category.QOL,
	description = "Stays connected when a packet fails to handle, logging it instead. " +
		"That means staying on a server sending packets this client cannot read."
) {
	private var reportedAt = 0L
	private var suppressed = 0

	@JvmStatic
	fun keepsConnection(packet: Packet<*>, error: Exception): Boolean {
		if (!enabled) return false
		val now = System.currentTimeMillis()
		if (now - reportedAt < REPORT_INTERVAL_MS) {
			suppressed++
			return true
		}
		reportedAt = now
		log.error("Stayed connected after failing to handle {} ({} more suppressed since the last report)", packet, suppressed, error)
		suppressed = 0
		return true
	}

	override fun onDisabled() {
		reportedAt = 0L
		suppressed = 0
	}

	private const val REPORT_INTERVAL_MS = 10_000L

	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)
}
