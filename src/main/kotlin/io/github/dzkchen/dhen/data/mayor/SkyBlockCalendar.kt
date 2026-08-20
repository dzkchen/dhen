package io.github.dzkchen.dhen.data.mayor

object SkyBlockCalendar {
	const val ELECTION_MONTH = 3
	const val ELECTION_DAY = 27

	private const val HOUR = 50_000L
	private const val DAY = 24 * HOUR
	private const val MONTH = 31 * DAY
	private const val YEAR = 12 * MONTH
	private const val YEAR_ONE_STARTED = 1_560_275_700_000L
	private const val ELECTION_INTO_YEAR = (ELECTION_MONTH - 1) * MONTH + (ELECTION_DAY - 1) * DAY

	fun dateAt(epochMillis: Long): SkyBlockDate {
		var remaining = intoYear(epochMillis)
		val months = remaining / MONTH
		remaining -= months * MONTH
		return SkyBlockDate(yearAt(epochMillis), (months + 1).toInt(), (remaining / DAY + 1).toInt())
	}

	fun millisAt(year: Int, month: Int, day: Int): Long =
		YEAR_ONE_STARTED + (year - 1) * YEAR + (month - 1) * MONTH + (day - 1) * DAY

	fun electionYearAt(epochMillis: Long): Int =
		yearAt(epochMillis) - if (intoYear(epochMillis) < ELECTION_INTO_YEAR) 1 else 0

	fun electedAt(epochMillis: Long): Long = electionAt(electionYearAt(epochMillis))

	fun nextElectionAt(epochMillis: Long): Long = electionAt(electionYearAt(epochMillis) + 1)

	private fun yearAt(epochMillis: Long): Int = (Math.floorDiv(epochMillis - YEAR_ONE_STARTED, YEAR) + 1).toInt()

	private fun intoYear(epochMillis: Long): Long = Math.floorMod(epochMillis - YEAR_ONE_STARTED, YEAR)

	private fun electionAt(year: Int): Long = millisAt(year, ELECTION_MONTH, ELECTION_DAY)
}

class SkyBlockDate internal constructor(val year: Int, val month: Int, val day: Int)
