package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import java.util.Calendar

/** One cell of the month grid. Leading blanks carry nulls for both day fields. */
data class DayCell(
    val dayOfMonth: Int?,
    val dateMillis: Long?,
    val hasExpense: Boolean,
    val hasIncome: Boolean
)

/**
 * Builds the calendar month grid. Month arithmetic lives in [MonthRange], which Reports shares.
 *
 * The week start is supplied by the caller (Settings owns it) and defaults to Sunday. Whatever is
 * passed here must match the order the fragment paints its weekday header in, or the grid and its
 * labels disagree — which looks exactly like an off-by-one in the blank count below.
 */
object CalendarMonth {

    /**
     * Leading blank cells to align day 1 under its weekday column, then one cell per day of the
     * month, each flagged by whether any of [transactions] that day was an expense and/or income.
     * Transactions outside the month are ignored. No trailing blanks — the grid just ends.
     */
    fun cellsFor(
        monthStartMillis: Long,
        transactions: List<Transaction>,
        weekStart: Int = Calendar.SUNDAY
    ): List<DayCell> {
        val month = MonthRange.calendarAt(monthStartMillis)
        // The `+ 7) % 7` is what puts a Sunday in the last column of a Monday-start week rather
        // than producing -1.
        val leadingBlanks = (month.get(Calendar.DAY_OF_WEEK) - weekStart + 7) % 7
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)

        val expenseDays = mutableSetOf<Int>()
        val incomeDays = mutableSetOf<Int>()
        for (transaction in transactions) {
            val txDay = MonthRange.calendarAt(transaction.date)
            val sameMonth = txDay.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                txDay.get(Calendar.MONTH) == month.get(Calendar.MONTH)
            if (!sameMonth) continue
            val dayOfMonth = txDay.get(Calendar.DAY_OF_MONTH)
            if (transaction.type == TransactionType.EXPENSE) {
                expenseDays += dayOfMonth
            } else {
                incomeDays += dayOfMonth
            }
        }

        val cells = ArrayList<DayCell>(leadingBlanks + daysInMonth)
        repeat(leadingBlanks) { cells += DayCell(null, null, hasExpense = false, hasIncome = false) }
        for (day in 1..daysInMonth) {
            val dayCal = MonthRange.calendarAt(monthStartMillis)
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            cells += DayCell(
                dayOfMonth = day,
                dateMillis = dayCal.timeInMillis,
                hasExpense = day in expenseDays,
                hasIncome = day in incomeDays
            )
        }
        return cells
    }
}
