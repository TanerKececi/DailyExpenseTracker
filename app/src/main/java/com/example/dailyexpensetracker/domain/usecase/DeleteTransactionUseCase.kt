package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import javax.inject.Inject

class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
