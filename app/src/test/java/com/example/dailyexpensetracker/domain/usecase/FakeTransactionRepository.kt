package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeTransactionRepository : TransactionRepository {
    private val state = MutableStateFlow<List<Transaction>>(emptyList())

    fun setTransactions(transactions: List<Transaction>) {
        state.value = transactions
    }

    override fun getAll(): Flow<List<Transaction>> = state.asStateFlow()

    override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> =
        state.map { list -> list.filter { it.date in start..end } }

    override fun getRecent(limit: Int): Flow<List<Transaction>> =
        state.map { list -> list.sortedByDescending { it.date }.take(limit) }

    override fun getSumByTypeAndDateRange(type: TransactionType, start: Long, end: Long): Flow<Double?> =
        state.map { list -> list.filter { it.type == type && it.date in start..end }.sumOf { it.amount } }

    override fun getByStatus(status: TransactionStatus): Flow<List<Transaction>> =
        state.map { list -> list.filter { it.status == status } }

    override fun getById(id: Long): Flow<Transaction?> =
        state.map { list -> list.find { it.id == id } }

    override suspend fun add(transaction: Transaction) {
        state.value = state.value + transaction
    }

    override suspend fun update(transaction: Transaction) {
        state.value = state.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun updateStatus(id: Long, status: TransactionStatus) {
        state.value = state.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}
