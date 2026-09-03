package com.example.dailyexpensetracker.ui.billdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.usecase.DeleteTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCardsUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionByIdUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateTransactionStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BillDetailUiState(
    val transaction: Transaction? = null,
    val category: Category? = null,
    val card: Card? = null
)

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getTransactionById: GetTransactionByIdUseCase,
    getCategories: GetCategoriesUseCase,
    getCards: GetCardsUseCase,
    private val updateTransactionStatus: UpdateTransactionStatusUseCase,
    private val deleteTransaction: DeleteTransactionUseCase
) : ViewModel() {

    private val transactionId: Long = checkNotNull(savedStateHandle.get<Long>("transactionId"))

    val uiState: StateFlow<BillDetailUiState> = combine(
        getTransactionById(transactionId), getCategories(), getCards()
    ) { transaction, categories, cards ->
        BillDetailUiState(
            transaction = transaction,
            category = transaction?.let { tx -> categories.find { it.id == tx.categoryId } },
            card = transaction?.cardId?.let { id -> cards.find { it.id == id } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillDetailUiState())

    suspend fun approve() {
        updateTransactionStatus(transactionId, TransactionStatus.PAID)
    }

    suspend fun decline() {
        deleteTransaction(transactionId)
    }
}
