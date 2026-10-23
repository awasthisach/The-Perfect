package com.vvf.smartmanager

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Process-wide, buffered Drive OAuth result queue.
 *
 * Unlike a SharedFlow with replay = 0, this queue retains a result when the
 * Cloud route's collector is temporarily absent during Activity recreation.
 * A result is delivered to one collector and is not replayed to later collectors.
 */
object DriveAuthEvents {
    private val channel = Channel<Result<String>>(capacity = Channel.BUFFERED)

    val results: Flow<Result<String>> = channel.receiveAsFlow()

    fun emit(result: Result<String>) {
        check(channel.trySend(result).isSuccess) {
            "Unable to queue Google Drive authentication result"
        }
    }
}
