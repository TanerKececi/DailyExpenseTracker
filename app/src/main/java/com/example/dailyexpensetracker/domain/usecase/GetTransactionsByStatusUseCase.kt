package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTransactionsByStatusUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    operator fun invoke(status: TransactionStatus): Flow<List<Transaction>> = repository.getByStatus(status)
}
