package com.example.dailyexpensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.dailyexpensetracker.domain.model.CardType

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardName: String,
    val cardNumberMasked: String,
    val currentBalance: Double,
    val cardType: CardType
)
