package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCardsUseCase @Inject constructor(
    private val repository: CardRepository
) {
    operator fun invoke(): Flow<List<Card>> = repository.getAll()
}
