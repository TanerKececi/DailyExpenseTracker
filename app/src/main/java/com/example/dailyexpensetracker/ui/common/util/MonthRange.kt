package com.example.dailyexpensetracker.ui.common.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Month and day arithmetic in the device's local timezone. Free of Android types so it
 * unit-tests on the JVM.
 *
 * Every day comparison goes through [dayStart] rather than dividing millis by a day's length,
 * which would drift by the UTC offset and misfile late-evening values across DST.
 */
object MonthRange {

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

    /** Millis at 00:00 on the day containing [millis]. */
    fun dayStart(millis: Long): Long = calendarAt(millis).timeInMillis

    /**
     * The same day-of-month as [dayMillis], moved into the month starting at [monthStartMillis]
     * and clamped to that month's length — so Jan 31 into February lands on the 28th or 29th
     * rather than wrapping into March.
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
     * [count] consecutive month starts ending at (and including) [monthStartMillis], oldest first.
     * `monthsEndingAt(septemberStart, 6)` returns April..September.
     */
    fun monthsEndingAt(monthStartMillis: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val months = ArrayList<Long>(count)
        var cursor = monthStartMillis
        repeat(count) {
            months += cursor
            cursor = previousMonthStart(cursor)
        }
        return months.reversed()
    }

    /**
     * A Calendar at [millis] with the time-of-day zeroed, in the device's local timezone.
     * Public so calendar-specific callers can read weekday and month-length fields off it.
     */
    fun calendarAt(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
