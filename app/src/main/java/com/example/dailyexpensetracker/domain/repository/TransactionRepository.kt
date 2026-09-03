package com.example.dailyexpensetracker.domain.repository

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    fun getRecent(limit: Int): Flow<List<Transaction>>
    fun getSumByTypeAndDateRange(type: TransactionType, start: Long, end: Long): Flow<Double?>
    suspend fun add(transaction: Transaction)
}
