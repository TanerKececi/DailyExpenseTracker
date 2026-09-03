package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.data.local.entity.TransactionEntity
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private fun TransactionEntity.toDomain() = Transaction(
    id = id,
    title = title,
    amount = amount,
    date = date,
    categoryId = categoryId,
    cardId = cardId,
    type = type,
    isScheduled = isScheduled,
    status = status
)

private fun Transaction.toEntity() = TransactionEntity(
    id = id,
    title = title,
    amount = amount,
    date = date,
    categoryId = categoryId,
    cardId = cardId,
    type = type,
    isScheduled = isScheduled,
    status = status
)

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao
) : TransactionRepository {
    override fun getAll(): Flow<List<Transaction>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> =
        dao.getByDateRange(start, end).map { list -> list.map { it.toDomain() } }

    override fun getRecent(limit: Int): Flow<List<Transaction>> =
        dao.getRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun getSumByTypeAndDateRange(type: TransactionType, start: Long, end: Long): Flow<Double?> =
        dao.getSumByTypeAndDateRange(type, start, end)

    override suspend fun add(transaction: Transaction) {
        dao.insert(transaction.toEntity())
    }
}
