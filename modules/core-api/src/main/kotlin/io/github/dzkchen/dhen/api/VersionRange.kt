package io.github.dzkchen.dhen.api

data class ApiVersion(private val parts: List<Int>) : Comparable<ApiVersion> {
	init {
		require(parts.isNotEmpty()) { "Version must have at least one part" }
		require(parts.all { it >= 0 }) { "Version parts must not be negative" }
	}

	override fun compareTo(other: ApiVersion): Int {
		val max = maxOf(parts.size, other.parts.size)
		for (index in 0 until max) {
			val diff = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
			if (diff != 0) return diff
		}
		return 0
	}

	override fun toString(): String = parts.joinToString(".")

	companion object {
		private val PATTERN = Regex("[0-9]+(\\.[0-9]+)*")

		fun parse(value: String): ApiVersion {
			val trimmed = value.trim()
			require(PATTERN.matches(trimmed)) { "Invalid version: '$value'" }
			return ApiVersion(trimmed.split('.').map(String::toInt))
		}
	}
}

data class VersionRange(
	val lower: ApiVersion? = null,
	val includeLower: Boolean = true,
	val upper: ApiVersion? = null,
	val includeUpper: Boolean = false,
) {
	init {
		if (lower != null && upper != null) {
			val order = lower.compareTo(upper)
			require(order < 0 || order == 0 && includeLower && includeUpper) { "Version range is empty: $this" }
		}
	}

	fun contains(version: ApiVersion): Boolean =
		(lower == null || if (includeLower) version >= lower else version > lower) &&
			(upper == null || if (includeUpper) version <= upper else version < upper)

	fun contains(version: String): Boolean = contains(ApiVersion.parse(version))

	override fun toString(): String {
		if (lower == null && upper == null) return "*"
		val open = if (includeLower) "[" else "("
		val close = if (includeUpper) "]" else ")"
		return "$open${lower ?: ""},${upper ?: ""}$close"
	}

	companion object {
		private val RANGE = Regex("""^([\[(])\s*([^,]*)\s*,\s*([^\])]*)\s*([\])])$""")
		val ANY = VersionRange()

		fun parse(value: String): VersionRange {
			val trimmed = value.trim()
			if (trimmed == "*") return ANY
			val match = RANGE.matchEntire(trimmed) ?: throw IllegalArgumentException("Invalid version range: '$value'")
			val lower = match.groupValues[2].takeIf(String::isNotBlank)?.let(ApiVersion::parse)
			val upper = match.groupValues[3].takeIf(String::isNotBlank)?.let(ApiVersion::parse)
			require(lower != null || upper != null) { "Version range must have at least one bound: '$value'" }
			return VersionRange(
				lower = lower,
				includeLower = match.groupValues[1] == "[",
				upper = upper,
				includeUpper = match.groupValues[4] == "]",
			)
		}
	}
}
