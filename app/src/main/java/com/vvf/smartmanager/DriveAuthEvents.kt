package com.vvf.smartmanager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide Drive OAuth result bus. Survives Activity recreation (rotation / config change).
 *
 * Wire-up (MainActivity):
 * - On sign-in activity result: `DriveAuthEvents.emit(result)` (after applying token).
 * - In Compose: `LaunchedEffect(Unit) { DriveAuthEvents.results.collect { ... } }`
 * - Remove class-level `pendingGoogleDriveSignInCallback` (cleared on recreation).
 */
object DriveAuthEvents {
    private val _results = MutableSharedFlow<Result<String>>(extraBufferCapacity = 1)
    val results: SharedFlow<Result<String>> = _results.asSharedFlow()

    fun emit(result: Result<String>) {
        _results.tryEmit(result)
    }
}
