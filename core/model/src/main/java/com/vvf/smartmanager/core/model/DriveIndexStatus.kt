package com.vvf.smartmanager.core.model

/** Safe, token-free status for the local Google Drive search index. */
data class DriveIndexStatus(
    val status: String = "NOT_SYNCED",
    val indexedCount: Int = 0,
    val capped: Boolean = false,
    val message: String = "",
    val updatedAt: Long = 0L
) {
    val isRunning: Boolean get() = status == "SYNCING"
}
