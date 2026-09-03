package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalendarMonthTest {

    /** Local-time millis for a given date. [month] is 0-based, matching Calendar. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun tx(dateMillis: Long, type: TransactionType) = Transaction(
        id = 0,
        title = "t",
        amount = 1.0,
        date = dateMillis,
        categoryId = 1L,
        cardId = null,
        type = type,
        isScheduled = false,
        status = TransactionStatus.PAID
    )

    // September 2026 starts on a Tuesday -> 2 leading blanks, 30 days.
    @Test
    fun `cellsFor aligns the first day under its weekday column`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1)), emptyList())

        assertEquals(32, cells.size)
        assertEquals(listOf(null, null), cells.take(2).map { it.dayOfMonth })
        assertEquals(1, cells[2].dayOfMonth)
        assertEquals(30, cells.last().dayOfMonth)
    }

    // January 2026 starts on a Thursday -> 4 leading blanks, 31 days.
    @Test
    fun `cellsFor handles a 31-day month`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2026, Calendar.JANUARY, 15)), emptyList())

        assertEquals(35, cells.size)
        assertEquals(4, cells.count { it.dayOfMonth == null })
        assertEquals(31, cells.last().dayOfMonth)
    }

    // February 2024 is a leap February starting on a Thursday -> 4 blanks, 29 days.
    @Test
    fun `cellsFor handles a leap February`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2024, Calendar.FEBRUARY, 10)), emptyList())

        assertEquals(33, cells.size)
        assertEquals(29, cells.last().dayOfMonth)
    }

    // February 2026 is a non-leap February starting on a Sunday -> 0 blanks, 28 days.
    @Test
    fun `cellsFor handles a non-leap February`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2026, Calendar.FEBRUARY, 10)), emptyList())

        assertEquals(28, cells.size)
        assertEquals(1, cells.first().dayOfMonth)
        assertEquals(28, cells.last().dayOfMonth)
    }

    @Test
    fun `cellsFor sets expense and income flags independently`() {
        val monthStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(
                tx(at(2026, Calendar.SEPTEMBER, 3), TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 5), TransactionType.INCOME),
                tx(at(2026, Calendar.SEPTEMBER, 7), TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 7), TransactionType.INCOME)
            )
        )
        fun day(n: Int) = cells.first { it.dayOfMonth == n }

        assertTrue(day(3).hasExpense)
        assertFalse(day(3).hasIncome)
        assertFalse(day(5).hasExpense)
        assertTrue(day(5).hasIncome)
        assertTrue(day(7).hasExpense)
        assertTrue(day(7).hasIncome)
        assertFalse(day(4).hasExpense)
        assertFalse(day(4).hasIncome)
    }

    @Test
    fun `cellsFor buckets a late-evening transaction on its local day`() {
        val monthStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(tx(at(2026, Calendar.SEPTEMBER, 15, hour = 23, minute = 30), TransactionType.EXPENSE))
        )

        assertTrue(cells.first { it.dayOfMonth == 15 }.hasExpense)
        assertFalse(cells.first { it.dayOfMonth == 16 }.hasExpense)
    }

    @Test
    fun `cellsFor ignores transactions outside the month`() {
        val monthStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(tx(at(2026, Calendar.AUGUST, 15), TransactionType.EXPENSE))
        )

        assertTrue(cells.none { it.hasExpense })
    }

    @Test
    fun `sameDayInMonth clamps to the shorter target month`() {
        val jan31 = at(2026, Calendar.JANUARY, 31)
        val februaryStart = CalendarMonth.monthStart(at(2026, Calendar.FEBRUARY, 1))

        val result = CalendarMonth.sameDayInMonth(februaryStart, jan31)

        assertEquals(28, Calendar.getInstance().apply { timeInMillis = result }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `sameDayInMonth keeps the day when the target month is long enough`() {
        val jan15 = at(2026, Calendar.JANUARY, 15)
        val februaryStart = CalendarMonth.monthStart(at(2026, Calendar.FEBRUARY, 1))

        val result = CalendarMonth.sameDayInMonth(februaryStart, jan15)

        assertEquals(15, Calendar.getInstance().apply { timeInMillis = result }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `dayStart zeroes the time of day`() {
        val result = CalendarMonth.dayStart(at(2026, Calendar.SEPTEMBER, 4, hour = 17, minute = 45))
        val cal = Calendar.getInstance().apply { timeInMillis = result }

        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(4, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `month navigation moves one month in each direction`() {
        val septemberStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 10))

        val october = Calendar.getInstance().apply { timeInMillis = CalendarMonth.nextMonthStart(septemberStart) }
        val august = Calendar.getInstance().apply { timeInMillis = CalendarMonth.previousMonthStart(septemberStart) }

        assertEquals(Calendar.OCTOBER, october.get(Calendar.MONTH))
        assertEquals(1, october.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, august.get(Calendar.MONTH))
    }

    @Test
    fun `label formats month and year`() {
        assertEquals("September 2026", CalendarMonth.label(CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 4))))
    }
}
