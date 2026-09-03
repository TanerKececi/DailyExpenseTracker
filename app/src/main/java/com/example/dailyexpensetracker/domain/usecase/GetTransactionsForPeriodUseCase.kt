package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTransactionsForPeriodUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    operator fun invoke(start: Long, end: Long): Flow<List<Transaction>> = repository.getByDateRange(start, end)
}
