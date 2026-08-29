package io.github.dzkchen.dhen.privacy

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ServerPacks {
	enum class Mode { OFF, MANUAL, ALWAYS_ON }

	const val MANUAL = "Manual"
	const val ALWAYS_ON = "Always On"

	val CHOOSABLE_MODES = listOf(MANUAL, ALWAYS_ON)

	@Volatile
	var mode: Mode = Mode.OFF

	private val wrapped = ConcurrentHashMap.newKeySet<UUID>()
	private val full = ConcurrentHashMap.newKeySet<UUID>()

	fun effectiveMode(enabled: Boolean, choice: String): Mode = when {
		!enabled -> Mode.OFF
		choice == ALWAYS_ON -> Mode.ALWAYS_ON
		else -> Mode.MANUAL
	}

	@JvmStatic
	fun pushed(id: UUID) {
		if (mode == Mode.OFF) return
		if (mode == Mode.ALWAYS_ON) full -= id else full += id
		wrapped += id
	}

	@JvmStatic
	fun popped(id: UUID?) {
		if (id == null) {
			forgetAll()
			return
		}
		wrapped -= id
		full -= id
	}

	fun forgetAll() {
		wrapped.clear()
		full.clear()
	}

	@JvmStatic
	fun isWrapped(id: UUID): Boolean = mode != Mode.OFF && id in wrapped

	fun stripsContent(id: UUID): Boolean = isWrapped(id) && id !in full
}
