package com.vvf.smartmanager.core.background

/**
 * Background WorkManager tags, unique work names, and interval constants.
 */
object BackgroundConstants {
    const val UNIQUE_STORAGE_ANALYZER_WORK = "vvf_storage_analyzer_work"
    const val UNIQUE_CLOUD_SYNC_WORK = "vvf_cloud_sync_work"
    const val UNIQUE_CLEANER_ANALYSIS_WORK = "vvf_cleaner_analysis_work"
    const val PERIODIC_SYNC_INTERVAL_HOURS = 6L
}
