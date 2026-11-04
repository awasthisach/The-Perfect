package com.vvf.smartmanager

import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Maps expected storage failures to WorkManager outcomes while preserving coroutine cancellation.
 * Keeping this boundary explicit prevents cancellation from becoming a retry or permanent error.
 */
internal suspend fun mapFileIndexingOutcome(
    onUnexpectedError: (Throwable) -> Unit = {},
    block: suspend () -> FileIndexingOutcome
): FileIndexingOutcome = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (securityError: SecurityException) {
    FileIndexingOutcome.PermissionRequired(
        securityError.message ?: "storage access was revoked"
    )
} catch (ioError: IOException) {
    FileIndexingOutcome.RetryableFailure(
        ioError.message ?: "storage I/O failed"
    )
} catch (error: Throwable) {
    onUnexpectedError(error)
    FileIndexingOutcome.PermanentFailure(
        error.message ?: "unexpected indexing failure"
    )
}
