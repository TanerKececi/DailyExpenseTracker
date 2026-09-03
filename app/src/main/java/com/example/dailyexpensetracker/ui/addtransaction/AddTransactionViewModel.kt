package com.example.dailyexpensetracker.ui.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.AddTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val getCategories: GetCategoriesUseCase,
    private val addTransaction: AddTransactionUseCase
) : ViewModel() {

    private val _type = MutableStateFlow(TransactionType.EXPENSE)
    val type: StateFlow<TransactionType> = _type.asStateFlow()

    val categories: StateFlow<List<Category>> = _type
        .flatMapLatest { getCategories(it == TransactionType.EXPENSE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dateMillis = MutableStateFlow(Calendar.getInstance().timeInMillis)
    val dateMillis: StateFlow<Long> = _dateMillis.asStateFlow()

    private var selectedCategoryId: Long? = null

    private val _events = MutableSharedFlow<AddTransactionEvent>()
    val events: SharedFlow<AddTransactionEvent> = _events

    fun setType(type: TransactionType) {
        _type.value = type
        selectedCategoryId = null
    }

    fun setDate(dateMillis: Long) {
        _dateMillis.value = dateMillis
    }

    fun setCategory(categoryId: Long) {
        selectedCategoryId = categoryId
    }

    fun save(title: String, amountText: String) {
        viewModelScope.launch {
            val amount = amountText.toDoubleOrNull()
            when {
                title.isBlank() -> _events.emit(AddTransactionEvent.Error(R.string.add_transaction_error_title))
                amount == null || amount <= 0.0 -> _events.emit(AddTransactionEvent.Error(R.string.add_transaction_error_amount))
                selectedCategoryId == null -> _events.emit(AddTransactionEvent.Error(R.string.add_transaction_error_category))
                else -> {
                    addTransaction(
                        Transaction(
                            id = 0,
                            title = title.trim(),
                            amount = amount,
                            date = _dateMillis.value,
                            categoryId = selectedCategoryId!!,
                            cardId = null,
                            type = _type.value,
                            isScheduled = false,
                            status = TransactionStatus.PAID
                        )
                    )
                    _events.emit(AddTransactionEvent.Saved)
                }
            }
        }
    }
}
