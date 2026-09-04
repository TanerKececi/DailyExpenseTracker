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
 * ponytail: the week is fixed to start on Sunday so it stays in sync with the static weekday
 * header row; switch to Calendar.firstDayOfWeek if locale-aware week starts are ever needed.
 */
object CalendarMonth {

    /**
     * Leading blank cells to align day 1 under its weekday column, then one cell per day of the
     * month, each flagged by whether any of [transactions] that day was an expense and/or income.
     * Transactions outside the month are ignored. No trailing blanks — the grid just ends.
     */
    fun cellsFor(monthStartMillis: Long, transactions: List<Transaction>): List<DayCell> {
        val month = MonthRange.calendarAt(monthStartMillis)
        val leadingBlanks = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
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
