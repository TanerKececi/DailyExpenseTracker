package com.example.dailyexpensetracker.domain.repository

interface DataResetRepository {
    /** Clears every table and repopulates it with the sample data shipped in DatabaseSeeder. */
    suspend fun resetToSeed()
}
