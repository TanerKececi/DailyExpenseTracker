package com.example.dailyexpensetracker.ui.bills

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BillBucketsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val now = at(2026, Calendar.SEPTEMBER, 10, hour = 14)

    private fun bill(id: Long, dueMillis: Long, status: TransactionStatus) = Transaction(
        id = id,
        title = "Bill $id",
        amount = 10.0,
        date = dueMillis,
        categoryId = 1L,
        cardId = null,
        type = TransactionType.EXPENSE,
        isScheduled = true,
        status = status
    )

    @Test
    fun `a bill due in the future is not overdue`() {
        val future = bill(1, at(2026, Calendar.SEPTEMBER, 20), TransactionStatus.UPCOMING)

        assertFalse(BillBuckets.isOverdue(future, now))
    }

    @Test
    fun `a bill due before today is overdue`() {
        val past = bill(1, at(2026, Calendar.SEPTEMBER, 9), TransactionStatus.UPCOMING)

        assertTrue(BillBuckets.isOverdue(past, now))
    }

    // The edge that matters: a bill due today is still due, not late.
    @Test
    fun `a bill due today is not overdue`() {
        val today = bill(1, at(2026, Calendar.SEPTEMBER, 10, hour = 1), TransactionStatus.UPCOMING)

        assertFalse(BillBuckets.isOverdue(today, now))
    }

    @Test
    fun `a bill due later today is not overdue even though its time has passed`() {
        val earlierToday = bill(1, at(2026, Calendar.SEPTEMBER, 10, hour = 9), TransactionStatus.UPCOMING)

        assertFalse(BillBuckets.isOverdue(earlierToday, now))
    }

    @Test
    fun `upcoming keeps only bills that are still due`() {
        val stored = listOf(
            bill(1, at(2026, Calendar.SEPTEMBER, 20), TransactionStatus.UPCOMING),
            bill(2, at(2026, Calendar.SEPTEMBER, 9), TransactionStatus.UPCOMING),
            bill(3, at(2026, Calendar.SEPTEMBER, 10), TransactionStatus.UPCOMING)
        )

        assertEquals(listOf(1L, 3L), BillBuckets.upcoming(stored, now).map { it.id })
    }

    @Test
    fun `overdue combines stored overdue with upcoming bills whose date has passed`() {
        val storedOverdue = listOf(bill(10, at(2026, Calendar.AUGUST, 1), TransactionStatus.OVERDUE))
        val storedUpcoming = listOf(
            bill(1, at(2026, Calendar.SEPTEMBER, 20), TransactionStatus.UPCOMING),
            bill(2, at(2026, Calendar.SEPTEMBER, 9), TransactionStatus.UPCOMING)
        )

        assertEquals(listOf(10L, 2L), BillBuckets.overdue(storedOverdue, storedUpcoming, now).map { it.id })
    }

    @Test
    fun `a bill appears in exactly one of the two buckets`() {
        val storedUpcoming = listOf(
            bill(1, at(2026, Calendar.SEPTEMBER, 20), TransactionStatus.UPCOMING),
            bill(2, at(2026, Calendar.SEPTEMBER, 9), TransactionStatus.UPCOMING),
            bill(3, at(2026, Calendar.SEPTEMBER, 10), TransactionStatus.UPCOMING)
        )

        val upcomingIds = BillBuckets.upcoming(storedUpcoming, now).map { it.id }
        val overdueIds = BillBuckets.overdue(emptyList(), storedUpcoming, now).map { it.id }

        assertEquals(storedUpcoming.size, upcomingIds.size + overdueIds.size)
        assertTrue(upcomingIds.intersect(overdueIds.toSet()).isEmpty())
    }
}
