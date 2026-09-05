package com.vvf.smartmanager.core.background.workers

import com.vvf.smartmanager.core.domain.JunkCleanerUseCase

/**
 * Wires [JunkCleanerUseCase] into [JunkScanRuntime] for WorkManager workers.
 */
object JunkScanBootstrap {
    fun wire(junkCleanerUseCase: JunkCleanerUseCase) {
        JunkScanRuntime.configure {
            try {
                var last = com.vvf.smartmanager.core.model.CleanerScanResult(isScanning = true)
                junkCleanerUseCase.scanJunkFiles().collect { result ->
                    last = result
                }
                val junkCount = last.emptyFolders.size + last.largeFiles.size +
                    last.tempFiles.size + last.apkFiles.size +
                    last.duplicateGroups.sumOf { it.files.size }
                JunkScanOutcome.Completed(
                    totalScanned = last.totalScannedCount,
                    wastedBytes = last.wastedBytes,
                    junkItemCount = junkCount
                )
            } catch (e: SecurityException) {
                JunkScanOutcome.PermanentFailure(e.message ?: "storage access denied for junk scan")
            } catch (e: java.io.IOException) {
                JunkScanOutcome.RetryableFailure(e.message ?: "junk scan I/O failed")
            } catch (e: Exception) {
                JunkScanOutcome.RetryableFailure(e.message ?: "junk scan failed")
            }
        }
    }
}
