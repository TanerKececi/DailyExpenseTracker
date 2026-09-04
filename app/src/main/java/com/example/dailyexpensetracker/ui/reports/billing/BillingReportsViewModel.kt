package com.example.dailyexpensetracker.ui.reports.billing

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class BillingReportsViewModel @Inject constructor(
    private val getTransactionsForPeriod: GetTransactionsForPeriodUseCase
) : ViewModel() {

    /**
     * The six months ending at [monthStart], fetched as a **single** range query and bucketed in
     * memory — six separate per-month flows would be six Room queries for the same data.
     */
    fun totalsFor(monthStart: Long): Flow<List<MonthTotal>> {
        val months = MonthRange.monthsEndingAt(monthStart, MONTHS_SHOWN)
        val rangeStart = months.first()
        val rangeEnd = MonthRange.nextMonthStart(months.last()) - 1
        return getTransactionsForPeriod(rangeStart, rangeEnd).map { MonthlyTotals.bucket(months, it) }
    }

    companion object {
        const val MONTHS_SHOWN = 6
    }
}
