package com.vvf.smartmanager

/**
 * Maximum number of primary-storage entries indexed in one pass.
 *
 * A scan that reaches this bound may have been truncated; its result is not a complete
 * inventory and therefore must not be used to prune rows that were not observed.
 */
internal const val PRIMARY_STORAGE_INDEX_LIMIT = 10_000

/**
 * Stale-row pruning is safe only when the scan finished below its configured bound.
 * Reaching the bound is treated conservatively as a potentially incomplete scan.
 */
internal fun shouldPruneStalePrimaryIndexRows(
    scannedItemCount: Int,
    scanCompleted: Boolean
): Boolean {
    require(scannedItemCount >= 0) { "scannedItemCount must not be negative" }
    return scanCompleted && scannedItemCount < PRIMARY_STORAGE_INDEX_LIMIT
}
