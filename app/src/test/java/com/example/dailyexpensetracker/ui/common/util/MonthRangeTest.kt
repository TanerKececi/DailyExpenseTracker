package com.example.dailyexpensetracker.ui.common.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthRangeTest {

    /** Local-time millis for a given date. [month] is 0-based, matching Calendar. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayOf(millis: Long) =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    @Test
    fun `sameDayInMonth clamps to the shorter target month`() {
        val jan31 = at(2026, Calendar.JANUARY, 31)
        val februaryStart = MonthRange.monthStart(at(2026, Calendar.FEBRUARY, 1))

        assertEquals(28, dayOf(MonthRange.sameDayInMonth(februaryStart, jan31)))
    }

    @Test
    fun `sameDayInMonth keeps the day when the target month is long enough`() {
        val jan15 = at(2026, Calendar.JANUARY, 15)
        val februaryStart = MonthRange.monthStart(at(2026, Calendar.FEBRUARY, 1))

        assertEquals(15, dayOf(MonthRange.sameDayInMonth(februaryStart, jan15)))
    }

    @Test
    fun `dayStart zeroes the time of day`() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = MonthRange.dayStart(at(2026, Calendar.SEPTEMBER, 4, hour = 17, minute = 45))
        }

        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(4, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `month navigation moves one month in each direction`() {
        val septemberStart = MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 10))

        val october = Calendar.getInstance().apply { timeInMillis = MonthRange.nextMonthStart(septemberStart) }
        val august = Calendar.getInstance().apply { timeInMillis = MonthRange.previousMonthStart(septemberStart) }

        assertEquals(Calendar.OCTOBER, october.get(Calendar.MONTH))
        assertEquals(1, october.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, august.get(Calendar.MONTH))
    }

    @Test
    fun `label formats month and year`() {
        assertEquals("September 2026", MonthRange.label(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 4))))
    }

    @Test
    fun `monthsEndingAt returns count months oldest first, ending at the given month`() {
        val septemberStart = MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 15))

        val months = MonthRange.monthsEndingAt(septemberStart, 6)

        assertEquals(6, months.size)
        assertEquals(septemberStart, months.last())
        assertEquals(MonthRange.monthStart(at(2026, Calendar.APRIL, 1)), months.first())
    }

    @Test
    fun `monthsEndingAt crosses a year boundary`() {
        val februaryStart = MonthRange.monthStart(at(2027, Calendar.FEBRUARY, 3))

        val months = MonthRange.monthsEndingAt(februaryStart, 4)

        assertEquals(MonthRange.monthStart(at(2026, Calendar.NOVEMBER, 1)), months.first())
        assertEquals(februaryStart, months.last())
    }

    @Test
    fun `monthsEndingAt returns empty for a non-positive count`() {
        assertEquals(
            emptyList<Long>(),
            MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.MAY, 1)), 0)
        )
    }
}
