package com.example.dailyexpensetracker.ui.reports.billing

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthlyTotalsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun tx(dateMillis: Long, amount: Double, type: TransactionType) = Transaction(
        id = 0,
        title = "t",
        amount = amount,
        date = dateMillis,
        categoryId = 1L,
        cardId = null,
        type = type,
        isScheduled = false,
        status = TransactionStatus.PAID
    )

    @Test
    fun `buckets transactions into their own month`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 3)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(
                tx(at(2026, Calendar.JULY, 5), 100.0, TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 9), 50.0, TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 20), 25.0, TransactionType.EXPENSE)
            )
        )

        assertEquals(3, totals.size)
        assertEquals(100.0, totals[0].expense, 0.001)
        assertEquals(0.0, totals[1].expense, 0.001)
        assertEquals(75.0, totals[2].expense, 0.001)
    }

    @Test
    fun `separates expense from income`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 1)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(
                tx(at(2026, Calendar.SEPTEMBER, 3), 200.0, TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 4), 900.0, TransactionType.INCOME)
            )
        )

        assertEquals(200.0, totals[0].expense, 0.001)
        assertEquals(900.0, totals[0].income, 0.001)
    }

    @Test
    fun `keeps empty months as zero rather than dropping them`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 6)

        val totals = MonthlyTotals.bucket(months, emptyList())

        assertEquals(6, totals.size)
        assertEquals(0.0, totals.sumOf { it.expense }, 0.001)
    }

    @Test
    fun `buckets correctly across a year boundary`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2027, Calendar.JANUARY, 1)), 3)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(
                tx(at(2026, Calendar.NOVEMBER, 10), 10.0, TransactionType.EXPENSE),
                tx(at(2027, Calendar.JANUARY, 2), 30.0, TransactionType.EXPENSE)
            )
        )

        assertEquals(10.0, totals[0].expense, 0.001)
        assertEquals(0.0, totals[1].expense, 0.001)
        assertEquals(30.0, totals[2].expense, 0.001)
    }

    @Test
    fun `ignores transactions outside the requested months`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 2)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(tx(at(2026, Calendar.JANUARY, 5), 500.0, TransactionType.EXPENSE))
        )

        assertEquals(0.0, totals.sumOf { it.expense }, 0.001)
    }

    @Test
    fun `labels each month with a short name`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 1)

        assertEquals("Sep", MonthlyTotals.bucket(months, emptyList())[0].label)
    }
}
