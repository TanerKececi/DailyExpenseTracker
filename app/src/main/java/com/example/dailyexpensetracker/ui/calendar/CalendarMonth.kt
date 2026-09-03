package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One cell of the month grid. Leading blanks carry nulls for both day fields. */
data class DayCell(
    val dayOfMonth: Int?,
    val dateMillis: Long?,
    val hasExpense: Boolean,
    val hasIncome: Boolean
)

/**
 * Month arithmetic and grid building. Deliberately free of Android types so it unit-tests on
 * the JVM without Robolectric — this is the only non-trivial logic in the Calendar screen.
 *
 * Every day comparison goes through [dayStart] rather than dividing millis by a day's length,
 * which would drift by the UTC offset and misfile late-evening transactions across DST.
 *
 * ponytail: the week is fixed to start on Sunday so it stays in sync with the static weekday
 * header row; switch to Calendar.firstDayOfWeek if locale-aware week starts are ever needed.
 */
object CalendarMonth {

    private val monthLabelFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    /** Millis at 00:00 on day 1 of the month containing [millis]. */
    fun monthStart(millis: Long): Long = calendarAt(millis).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /** Millis at 00:00 on day 1 of the following month — an exclusive end bound. */
    fun nextMonthStart(monthStartMillis: Long): Long = calendarAt(monthStartMillis).apply {
        add(Calendar.MONTH, 1)
    }.timeInMillis

    /** Millis at 00:00 on day 1 of the preceding month. */
    fun previousMonthStart(monthStartMillis: Long): Long = calendarAt(monthStartMillis).apply {
        add(Calendar.MONTH, -1)
    }.timeInMillis

    /** Millis at 00:00 on the day containing [millis], in the device's local timezone. */
    fun dayStart(millis: Long): Long = calendarAt(millis).timeInMillis

    /**
     * The same day-of-month as [dayMillis], moved into the month starting at [monthStartMillis]
     * and clamped to that month's length — so navigating from Jan 31 to February lands on the
     * 28th (or 29th) rather than wrapping into March.
     */
    fun sameDayInMonth(monthStartMillis: Long, dayMillis: Long): Long {
        val day = calendarAt(dayMillis).get(Calendar.DAY_OF_MONTH)
        val target = calendarAt(monthStartMillis)
        target.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(target.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return target.timeInMillis
    }

    /** e.g. "September 2026". */
    fun label(monthStartMillis: Long): String = monthLabelFormat.format(Date(monthStartMillis))

    /**
     * Leading blank cells to align day 1 under its weekday column, then one cell per day of the
     * month, each flagged by whether any of [transactions] that day was an expense and/or income.
     * Transactions outside the month are ignored. No trailing blanks — the grid just ends.
     */
    fun cellsFor(monthStartMillis: Long, transactions: List<Transaction>): List<DayCell> {
        val month = calendarAt(monthStartMillis)
        val leadingBlanks = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)

        val expenseDays = mutableSetOf<Int>()
        val incomeDays = mutableSetOf<Int>()
        for (transaction in transactions) {
            val txDay = calendarAt(transaction.date)
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
            val dayCal = calendarAt(monthStartMillis)
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

    /** A Calendar at [millis] with the time-of-day zeroed, in the device's local timezone. */
    private fun calendarAt(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
