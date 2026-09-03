package com.example.dailyexpensetracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val getCategories: GetCategoriesUseCase
) : ViewModel() {

    private val _isExpense = MutableStateFlow(true)
    val isExpense: StateFlow<Boolean> = _isExpense.asStateFlow()

    val categories: StateFlow<List<Category>> = _isExpense
        .flatMapLatest { getCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setExpense(isExpense: Boolean) {
        _isExpense.value = isExpense
    }
}
