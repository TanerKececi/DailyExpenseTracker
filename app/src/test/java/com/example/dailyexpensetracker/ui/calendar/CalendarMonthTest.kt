package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.MonthRange
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
        val cells = CalendarMonth.cellsFor(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), emptyList())

        assertEquals(32, cells.size)
        assertEquals(listOf(null, null), cells.take(2).map { it.dayOfMonth })
        assertEquals(1, cells[2].dayOfMonth)
        assertEquals(30, cells.last().dayOfMonth)
    }

    // January 2026 starts on a Thursday -> 4 leading blanks, 31 days.
    @Test
    fun `cellsFor handles a 31-day month`() {
        val cells = CalendarMonth.cellsFor(MonthRange.monthStart(at(2026, Calendar.JANUARY, 15)), emptyList())

        assertEquals(35, cells.size)
        assertEquals(4, cells.count { it.dayOfMonth == null })
        assertEquals(31, cells.last().dayOfMonth)
    }

    // February 2024 is a leap February starting on a Thursday -> 4 blanks, 29 days.
    @Test
    fun `cellsFor handles a leap February`() {
        val cells = CalendarMonth.cellsFor(MonthRange.monthStart(at(2024, Calendar.FEBRUARY, 10)), emptyList())

        assertEquals(33, cells.size)
        assertEquals(29, cells.last().dayOfMonth)
    }

    // February 2026 is a non-leap February starting on a Sunday -> 0 blanks, 28 days.
    @Test
    fun `cellsFor handles a non-leap February`() {
        val cells = CalendarMonth.cellsFor(MonthRange.monthStart(at(2026, Calendar.FEBRUARY, 10)), emptyList())

        assertEquals(28, cells.size)
        assertEquals(1, cells.first().dayOfMonth)
        assertEquals(28, cells.last().dayOfMonth)
    }

    @Test
    fun `cellsFor sets expense and income flags independently`() {
        val monthStart = MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1))
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
        val monthStart = MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(tx(at(2026, Calendar.SEPTEMBER, 15, hour = 23, minute = 30), TransactionType.EXPENSE))
        )

        assertTrue(cells.first { it.dayOfMonth == 15 }.hasExpense)
        assertFalse(cells.first { it.dayOfMonth == 16 }.hasExpense)
    }

    @Test
    fun `cellsFor ignores transactions outside the month`() {
        val monthStart = MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(tx(at(2026, Calendar.AUGUST, 15), TransactionType.EXPENSE))
        )

        assertTrue(cells.none { it.hasExpense })
    }

    // 1 March 2026 falls on a Sunday; 1 June 2026 falls on a Monday.

    @Test
    fun `a Sunday-start month needs no leading blanks on a Sunday week`() {
        val marchStart = MonthRange.monthStart(at(2026, Calendar.MARCH, 1))

        val cells = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.SUNDAY)

        assertEquals(1, cells.first().dayOfMonth)
    }

    @Test
    fun `a Sunday-start month needs six leading blanks on a Monday week`() {
        val marchStart = MonthRange.monthStart(at(2026, Calendar.MARCH, 1))

        val cells = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.MONDAY)

        assertEquals(6, cells.count { it.dayOfMonth == null })
        assertEquals(1, cells[6].dayOfMonth)
    }

    @Test
    fun `a Monday-start month needs no leading blanks on a Monday week`() {
        val juneStart = MonthRange.monthStart(at(2026, Calendar.JUNE, 1))

        val cells = CalendarMonth.cellsFor(juneStart, emptyList(), Calendar.MONDAY)

        assertEquals(1, cells.first().dayOfMonth)
    }

    @Test
    fun `week start does not change how many real days the month has`() {
        val marchStart = MonthRange.monthStart(at(2026, Calendar.MARCH, 1))

        val sunday = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.SUNDAY)
        val monday = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.MONDAY)

        assertEquals(31, sunday.count { it.dayOfMonth != null })
        assertEquals(31, monday.count { it.dayOfMonth != null })
    }

    @Test
    fun `the default week start is Sunday`() {
        val marchStart = MonthRange.monthStart(at(2026, Calendar.MARCH, 1))

        assertEquals(
            CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.SUNDAY),
            CalendarMonth.cellsFor(marchStart, emptyList())
        )
    }
}
