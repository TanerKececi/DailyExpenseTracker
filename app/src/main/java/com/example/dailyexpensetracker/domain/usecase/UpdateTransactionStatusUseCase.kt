package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import javax.inject.Inject

class UpdateTransactionStatusUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(id: Long, status: TransactionStatus) = repository.updateStatus(id, status)
}
