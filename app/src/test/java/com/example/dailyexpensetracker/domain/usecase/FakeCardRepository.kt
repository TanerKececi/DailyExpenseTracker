package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCardRepository : CardRepository {
    private val state = MutableStateFlow<List<Card>>(emptyList())

    fun setCards(cards: List<Card>) {
        state.value = cards
    }

    override fun getAll(): Flow<List<Card>> = state.asStateFlow()
}
