package io.github.dzkchen.dhen.data.mayor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkyBlockCalendarTest {
	@Test
	fun `the skyblock calendar starts at year one, day one`() {
		val date = SkyBlockCalendar.dateAt(YEAR_ONE)

		assertEquals(1, date.year)
		assertEquals(1, date.month)
		assertEquals(1, date.day)
	}

	@Test
	fun `a real timestamp reads back as the date the reference mods compute for it`() {
		val date = SkyBlockCalendar.dateAt(1787184000000L)

		assertEquals(509, date.year)
		assertEquals(4, date.month)
		assertEquals(22, date.day)
	}

	@Test
	fun `a timestamp late in a skyblock day still reads back as that day`() {
		val date = SkyBlockCalendar.dateAt(1787184950000L)

		assertEquals(509, date.year)
		assertEquals(4, date.month)
		assertEquals(23, date.day)
	}

	@Test
	fun `a date built from its parts reads back unchanged`() {
		val date = SkyBlockCalendar.dateAt(SkyBlockCalendar.millisAt(400, 3, 27))

		assertEquals(400, date.year)
		assertEquals(3, date.month)
		assertEquals(27, date.day)
	}

	@Test
	fun `the mayor is last year's until this year's election day arrives`() {
		val election = SkyBlockCalendar.millisAt(400, 3, 27)

		assertEquals(399, SkyBlockCalendar.electionYearAt(election - 1))
		assertEquals(400, SkyBlockCalendar.electionYearAt(election))
		assertEquals(400, SkyBlockCalendar.electionYearAt(SkyBlockCalendar.millisAt(400, 12, 31)))
		assertEquals(400, SkyBlockCalendar.electionYearAt(SkyBlockCalendar.millisAt(401, 1, 1)))
	}

	@Test
	fun `the election a mayor was seated by and the one that unseats them are a year apart`() {
		val summer = SkyBlockCalendar.millisAt(509, 6, 1)

		assertEquals(SkyBlockCalendar.millisAt(509, 3, 27), SkyBlockCalendar.electedAt(summer))
		assertEquals(SkyBlockCalendar.millisAt(510, 3, 27), SkyBlockCalendar.nextElectionAt(summer))
	}

	private companion object {
		private const val YEAR_ONE = 1_560_275_700_000L
	}
}
