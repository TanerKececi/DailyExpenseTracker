package com.example.dailyexpensetracker.domain.repository

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    fun getRecent(limit: Int): Flow<List<Transaction>>
    fun getSumByTypeAndDateRange(type: TransactionType, start: Long, end: Long): Flow<Double?>
    fun getByStatus(status: TransactionStatus): Flow<List<Transaction>>
    fun getById(id: Long): Flow<Transaction?>
    suspend fun add(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun updateStatus(id: Long, status: TransactionStatus)
    suspend fun delete(id: Long)
}
