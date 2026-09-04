package com.example.dailyexpensetracker.ui.bills

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.ui.common.util.MonthRange

/**
 * Sorts scheduled bills into the Upcoming and Overdue tabs.
 *
 * Overdue is *derived from the due date* rather than stored, so a bill moves tabs on its own as
 * time passes. Storing it would go stale the moment the due date passed, leaving a bill scheduled
 * for yesterday still labelled Upcoming forever — with no background job to correct it.
 *
 * Pure and Android-free so the date edges are unit-testable.
 */
object BillBuckets {

    /**
     * True when a bill's due date is strictly before today. A bill due *today* is not overdue,
     * which is why this compares whole days via [MonthRange.dayStart] rather than raw millis.
     */
    fun isOverdue(transaction: Transaction, nowMillis: Long): Boolean =
        MonthRange.dayStart(transaction.date) < MonthRange.dayStart(nowMillis)

    /** Of the stored-UPCOMING bills, the ones still genuinely due. */
    fun upcoming(storedUpcoming: List<Transaction>, nowMillis: Long): List<Transaction> =
        storedUpcoming.filterNot { isOverdue(it, nowMillis) }

    /** Stored-OVERDUE bills, plus stored-UPCOMING bills whose due date has passed. */
    fun overdue(
        storedOverdue: List<Transaction>,
        storedUpcoming: List<Transaction>,
        nowMillis: Long
    ): List<Transaction> = storedOverdue + storedUpcoming.filter { isOverdue(it, nowMillis) }
}
