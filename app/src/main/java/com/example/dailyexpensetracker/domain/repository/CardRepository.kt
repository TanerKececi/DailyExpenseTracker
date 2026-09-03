package com.example.dailyexpensetracker.domain.repository

import com.example.dailyexpensetracker.domain.model.Card
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun getAll(): Flow<List<Card>>
}
