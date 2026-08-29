package io.github.dzkchen.dhen.privacy

internal object Whitelist {
	const val AUTO = "Auto"
	const val CUSTOM = "Custom"
	const val BLOCK_ALL = "Block All"
	const val OFF = "Off"
	const val ON = "On"
	const val REQUIRED = "Required"

	val CHOOSABLE_MODES = listOf(AUTO, CUSTOM)
	val ENTRY_STATES = listOf(OFF, ON)

	private val LOCKED_MODE = listOf(BLOCK_ALL)
	private val LOCKED_ENTRY = listOf(REQUIRED)
	private const val SEPARATOR = " "

	fun modes(spoofing: Boolean): List<String> = if (spoofing) LOCKED_MODE else CHOOSABLE_MODES

	fun states(requiredBy: String?): List<String> = if (requiredBy == null) ENTRY_STATES else LOCKED_ENTRY

	fun effectiveMode(spoofing: Boolean, custom: Boolean): ModRegistry.Mode = when {
		spoofing -> ModRegistry.Mode.BLOCK_ALL
		custom -> ModRegistry.Mode.CUSTOM
		else -> ModRegistry.Mode.AUTO
	}

	fun encode(explicit: Set<String>): String = explicit.joinToString(SEPARATOR)

	fun decode(stored: String): Set<String> =
		stored.split(SEPARATOR).filterTo(LinkedHashSet(), String::isNotEmpty)

	fun describe(modId: String, requiredBy: String?): String =
		if (requiredBy == null) modId else "$modId — required by $requiredBy — uncheck the depender to release"
}
