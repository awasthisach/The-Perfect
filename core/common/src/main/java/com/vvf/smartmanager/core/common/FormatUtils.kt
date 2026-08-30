package com.vvf.smartmanager.core.common

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility functions for formatting storage sizes, dates, and file details.
 */
object FormatUtils {

    private val sizeFormat = DecimalFormat("#,##0.#")
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM, yy", Locale.getDefault())

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return "${sizeFormat.format(value)} ${units[digitGroups]}"
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        return dateFormat.format(Date(timestamp))
    }

    fun formatShortDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        return shortDateFormat.format(Date(timestamp))
    }

    fun formatPercentage(fraction: Float): String {
        val pct = (fraction * 100).coerceIn(0f, 100f)
        return "${sizeFormat.format(pct)}%"
    }
}
