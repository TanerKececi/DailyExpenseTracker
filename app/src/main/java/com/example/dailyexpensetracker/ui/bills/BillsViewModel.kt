package com.example.dailyexpensetracker.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsByStatusUseCase
import com.example.dailyexpensetracker.ui.wallet.adapter.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillsViewModel @Inject constructor(
    private val getTransactionsByStatus: GetTransactionsByStatusUseCase,
    getCategories: GetCategoriesUseCase
) : ViewModel() {

    private val _status = MutableStateFlow(TransactionStatus.PAID)
    private val _query = MutableStateFlow("")

    /** Read by the fragment to re-select the right tab on a view that outlived its ViewModel state. */
    val status: TransactionStatus get() = _status.value

    /**
     * Overdue is derived from the due date rather than read from the stored status, so a bill
     * slides from Upcoming into Overdue on its own. See [BillBuckets].
     */
    private val transactionsFlow = _status.flatMapLatest { status ->
        when (status) {
            TransactionStatus.PAID -> getTransactionsByStatus(TransactionStatus.PAID)

            TransactionStatus.UPCOMING -> getTransactionsByStatus(TransactionStatus.UPCOMING)
                .map { BillBuckets.upcoming(it, System.currentTimeMillis()) }

            TransactionStatus.OVERDUE -> combine(
                getTransactionsByStatus(TransactionStatus.OVERDUE),
                getTransactionsByStatus(TransactionStatus.UPCOMING)
            ) { storedOverdue, storedUpcoming ->
                BillBuckets.overdue(storedOverdue, storedUpcoming, System.currentTimeMillis())
            }
        }
    }

    val bills: StateFlow<List<TransactionListItem>> = combine(
        transactionsFlow, getCategories(), _query
    ) { transactions, categories, query ->
        val categoryById = categories.associateBy { it.id }
        transactions
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { TransactionListItem(it, categoryById[it.categoryId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStatus(status: TransactionStatus) {
        _status.value = status
    }

    fun setQuery(query: String) {
        _query.value = query
    }
}
