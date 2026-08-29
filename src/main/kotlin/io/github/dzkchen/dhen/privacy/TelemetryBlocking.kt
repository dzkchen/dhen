package io.github.dzkchen.dhen.privacy

import io.github.dzkchen.dhen.Dhen
import org.slf4j.LoggerFactory

object TelemetryBlocking {
	private val log = LoggerFactory.getLogger(Dhen.MOD_ID)

	@Volatile
	private var announced = false

	@JvmStatic
	fun refused() {
		if (announced) return
		announced = true
		log.info("Telemetry Blocking is refusing every client telemetry session")
	}
}
