package com.example.dailyexpensetracker.ui.addtransaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.AddTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.DeleteTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionByIdUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

sealed interface AddTransactionEvent {
    data object Saved : AddTransactionEvent
    data class Error(val messageRes: Int) : AddTransactionEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCategories: GetCategoriesUseCase,
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    getTransactionById: GetTransactionByIdUseCase
) : ViewModel() {

    /** 0 means "add a new transaction"; any other id means edit that one. */
    private val transactionId: Long = savedStateHandle.get<Long>("transactionId") ?: 0L
    val isEditing: Boolean = transactionId != 0L

    private val _type = MutableStateFlow(TransactionType.EXPENSE)
    val type: StateFlow<TransactionType> = _type.asStateFlow()

    val categories: StateFlow<List<Category>> = _type
        .flatMapLatest { getCategories(it == TransactionType.EXPENSE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dateMillis = MutableStateFlow(Calendar.getInstance().timeInMillis)
    val dateMillis: StateFlow<Long> = _dateMillis.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    /**
     * The transaction being edited, or null in add mode. A StateFlow rather than a one-shot event
     * because the sheet subscribes after the ViewModel is constructed and would miss an event.
     */
    private val _original = MutableStateFlow<Transaction?>(null)
    val original: StateFlow<Transaction?> = _original.asStateFlow()

    private val _events = MutableSharedFlow<AddTransactionEvent>()
    val events: SharedFlow<AddTransactionEvent> = _events

    init {
        if (isEditing) {
            viewModelScope.launch {
                val existing = getTransactionById(transactionId).first() ?: return@launch
                _type.value = existing.type
                _dateMillis.value = existing.date
                _selectedCategoryId.value = existing.categoryId
                _original.value = existing
            }
        }
    }

    fun setType(type: TransactionType) {
        _type.value = type
        // Categories are type-specific, so the previous selection cannot survive a type change.
        _selectedCategoryId.value = null
    }

    fun setDate(dateMillis: Long) {
        _dateMillis.value = dateMillis
    }

    fun setCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun save(title: String, amountText: String) {
        viewModelScope.launch {
            val amount = amountText.toDoubleOrNull()
            val categoryId = _selectedCategoryId.value
            when {
                title.isBlank() -> _events.emit(AddTransactionEvent.Error(R.string.add_transaction_error_title))
                amount == null || amount <= 0.0 -> _events.emit(AddTransactionEvent.Error(R.string.add_transaction_error_amount))
                categoryId == null -> _events.emit(AddTransactionEvent.Error(R.string.add_transaction_error_category))
                else -> {
                    val existing = _original.value
                    val transaction = Transaction(
                        id = existing?.id ?: 0,
                        title = title.trim(),
                        amount = amount,
                        date = _dateMillis.value,
                        categoryId = categoryId,
                        type = _type.value,
                        // Carry through the fields this sheet doesn't edit. Defaulting them would
                        // turn a scheduled bill into an ordinary paid transaction and drop its card.
                        cardId = existing?.cardId,
                        isScheduled = existing?.isScheduled ?: false,
                        status = existing?.status ?: TransactionStatus.PAID
                    )
                    if (existing != null) updateTransaction(transaction) else addTransaction(transaction)
                    _events.emit(AddTransactionEvent.Saved)
                }
            }
        }
    }

    fun delete() {
        val existing = _original.value ?: return
        viewModelScope.launch {
            deleteTransaction(existing.id)
            _events.emit(AddTransactionEvent.Saved)
        }
    }
}
