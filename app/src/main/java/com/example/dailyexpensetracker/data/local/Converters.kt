package com.example.dailyexpensetracker.data.local

import androidx.room.TypeConverter
import com.example.dailyexpensetracker.domain.model.CardType
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter
    fun fromCardType(value: CardType): String = value.name

    @TypeConverter
    fun toCardType(value: String): CardType = CardType.valueOf(value)
}
