package com.example.dailyexpensetracker.domain.model

data class Card(
    val id: Long,
    val cardName: String,
    val cardNumberMasked: String,
    val currentBalance: Double,
    val cardType: CardType
)
