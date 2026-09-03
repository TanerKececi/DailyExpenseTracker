package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.entity.CardEntity
import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private fun CardEntity.toDomain() = Card(
    id = id,
    cardName = cardName,
    cardNumberMasked = cardNumberMasked,
    currentBalance = currentBalance,
    cardType = cardType
)

class CardRepositoryImpl @Inject constructor(
    private val dao: CardDao
) : CardRepository {
    override fun getAll(): Flow<List<Card>> = dao.getAll().map { list -> list.map { it.toDomain() } }
}
