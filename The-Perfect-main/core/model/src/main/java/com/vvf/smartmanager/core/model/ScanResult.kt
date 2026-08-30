package com.vvf.smartmanager.core.model

/**
 * Result wrapper for storage scanning operations to prevent premature data deletion
 * on Android 14 partial media permissions or interrupted scans.
 */
sealed class ScanResult<out T> {
    data class Complete<out T>(
        val data: T,
        val totalScannedCount: Int,
        val scanDurationMs: Long
    ) : ScanResult<T>()

    data class Partial<out T>(
        val data: T,
        val scannedCount: Int,
        val reason: String = "Partial storage permission granted or scan interrupted"
    ) : ScanResult<T>()

    data class Failed(
        val exception: Throwable,
        val message: String
    ) : ScanResult<Nothing>()
}

enum class IndexScanState {
    COMPLETE,
    PARTIAL,
    FAILED
}
