package com.example.dailyexpensetracker.ui.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val itemDateFormat = SimpleDateFormat("d MMMM yyyy", Locale.US)
    private val pickerDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    fun formatForItem(dateMillis: Long): String = itemDateFormat.format(Date(dateMillis))
    fun formatForPicker(dateMillis: Long): String = pickerDateFormat.format(Date(dateMillis))
}
