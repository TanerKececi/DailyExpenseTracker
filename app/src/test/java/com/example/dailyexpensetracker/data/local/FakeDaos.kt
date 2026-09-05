package com.example.dailyexpensetracker.data.local

import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.data.local.entity.CardEntity
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import com.example.dailyexpensetracker.data.local.entity.TransactionEntity
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hands out ids the way AUTOINCREMENT does: monotonic, and deliberately **not** reset by a delete.
 * That detail is the whole point of these fakes — a sequence that restarted at 1 after a clear
 * would hide exactly the reseed bug [DatabaseSeederTest] exists to catch.
 */
private class IdSequence {
    private var next = 1L
    fun take(): Long = next++
}

class FakeCategoryDao : CategoryDao {
    val items = mutableListOf<CategoryEntity>()
    private val ids = IdSequence()
    private val state = MutableStateFlow<List<CategoryEntity>>(emptyList())

    private fun publish() {
        state.value = items.toList()
    }

    override fun getAll(): Flow<List<CategoryEntity>> = state

    override fun getByType(isExpense: Boolean): Flow<List<CategoryEntity>> =
        state.map { list -> list.filter { it.isExpense == isExpense } }

    override suspend fun updateBudget(id: Long, limit: Double?) {
        items.replaceAll { if (it.id == id) it.copy(budgetLimit = limit) else it }
        publish()
    }

    override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> {
        val assigned = categories.map { it.copy(id = ids.take()) }
        items += assigned
        publish()
        return assigned.map { it.id }
    }

    override suspend fun deleteAll() {
        items.clear()
        publish()
    }
}

class FakeCardDao : CardDao {
    val items = mutableListOf<CardEntity>()
    private val ids = IdSequence()
    private val state = MutableStateFlow<List<CardEntity>>(emptyList())

    private fun publish() {
        state.value = items.toList()
    }

    override fun getAll(): Flow<List<CardEntity>> = state

    override suspend fun insertAll(cards: List<CardEntity>): List<Long> {
        val assigned = cards.map { it.copy(id = ids.take()) }
        items += assigned
        publish()
        return assigned.map { it.id }
    }

    override suspend fun deleteAll() {
        items.clear()
        publish()
    }
}

class FakeTransactionDao : TransactionDao {
    val items = mutableListOf<TransactionEntity>()
    private val ids = IdSequence()
    private val state = MutableStateFlow<List<TransactionEntity>>(emptyList())

    private fun publish() {
        state.value = items.toList()
    }

    override fun getAll(): Flow<List<TransactionEntity>> = state

    override fun getByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>> =
        state.map { list -> list.filter { it.date in start..end } }

    override fun getRecent(limit: Int): Flow<List<TransactionEntity>> =
        state.map { list -> list.sortedByDescending { it.date }.take(limit) }

    override fun getSumByTypeAndDateRange(
        type: TransactionType,
        start: Long,
        end: Long
    ): Flow<Double?> = state.map { list ->
        list.filter { it.type == type && it.date in start..end }.sumOf { it.amount }
    }

    override fun getByStatus(status: TransactionStatus): Flow<List<TransactionEntity>> =
        state.map { list -> list.filter { it.status == status } }

    override fun getById(id: Long): Flow<TransactionEntity?> =
        state.map { list -> list.find { it.id == id } }

    override suspend fun updateStatus(id: Long, status: TransactionStatus) {
        items.replaceAll { if (it.id == id) it.copy(status = status) else it }
        publish()
    }

    override suspend fun delete(id: Long) {
        items.removeAll { it.id == id }
        publish()
    }

    override suspend fun update(transaction: TransactionEntity) {
        items.replaceAll { if (it.id == transaction.id) transaction else it }
        publish()
    }

    override suspend fun insert(transaction: TransactionEntity): Long {
        val assigned = transaction.copy(id = ids.take())
        items += assigned
        publish()
        return assigned.id
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        items += transactions.map { it.copy(id = ids.take()) }
        publish()
    }

    override suspend fun deleteAll() {
        items.clear()
        publish()
    }
}
