package com.example.dailyexpensetracker.ui.reports.billing

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthTotal(
    val monthStartMillis: Long,
    val label: String,
    val expense: Double,
    val income: Double
)

/**
 * Groups transactions into a fixed list of months. Pure, so it unit-tests on the JVM.
 *
 * Months with no transactions are kept as zeroes rather than dropped — the bar chart needs a
 * slot for every month or the x-axis silently lies about the period.
 */
object MonthlyTotals {

    private val shortMonthFormat = SimpleDateFormat("MMM", Locale.US)

    fun bucket(monthStarts: List<Long>, transactions: List<Transaction>): List<MonthTotal> {
        val expenseByMonth = HashMap<Long, Double>()
        val incomeByMonth = HashMap<Long, Double>()

        for (transaction in transactions) {
            val month = MonthRange.monthStart(transaction.date)
            if (transaction.type == TransactionType.EXPENSE) {
                expenseByMonth[month] = (expenseByMonth[month] ?: 0.0) + transaction.amount
            } else {
                incomeByMonth[month] = (incomeByMonth[month] ?: 0.0) + transaction.amount
            }
        }

        return monthStarts.map { monthStart ->
            MonthTotal(
                monthStartMillis = monthStart,
                label = shortMonthFormat.format(Date(monthStart)),
                expense = expenseByMonth[monthStart] ?: 0.0,
                income = incomeByMonth[monthStart] ?: 0.0
            )
        }
    }
}
