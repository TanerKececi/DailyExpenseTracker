package com.example.dailyexpensetracker.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.domain.usecase.GetBudgetSummaryUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCardsUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.ui.wallet.adapter.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class WalletUiState(
    val remaining: Double = 0.0,
    val totalBudget: Double = 0.0,
    val spent: Double = 0.0,
    val onTrack: Boolean = true,
    val cards: List<Card> = emptyList(),
    val recentTransactions: List<TransactionListItem> = emptyList()
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    getBudgetSummary: GetBudgetSummaryUseCase,
    getCards: GetCardsUseCase,
    getTransactionsForPeriod: GetTransactionsForPeriodUseCase,
    getCategories: GetCategoriesUseCase
) : ViewModel() {

    val uiState: StateFlow<WalletUiState> = combine(
        getBudgetSummary(startOfMonth(), endOfMonth()),
        getCards(),
        getTransactionsForPeriod(0L, Long.MAX_VALUE),
        getCategories()
    ) { summary, cards, transactions, categories ->
        val categoryById = categories.associateBy { it.id }
        val recent = transactions
            .sortedByDescending { it.date }
            .take(10)
            .map { TransactionListItem(it, categoryById[it.categoryId]) }

        WalletUiState(
            remaining = summary.remaining,
            totalBudget = summary.totalBudget,
            spent = summary.totalSpent,
            onTrack = summary.totalSpent <= summary.totalBudget,
            cards = cards,
            recentTransactions = recent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WalletUiState())

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
