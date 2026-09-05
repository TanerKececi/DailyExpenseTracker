package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.repository.DataResetRepository
import javax.inject.Inject

class ResetDataUseCase @Inject constructor(
    private val repository: DataResetRepository
) {
    suspend operator fun invoke() = repository.resetToSeed()
}
