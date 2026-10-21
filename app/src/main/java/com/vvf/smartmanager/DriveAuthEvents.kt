package com.vvf.smartmanager

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Process-wide, buffered delivery of Drive OAuth results.
 *
 * The channel survives Activity recreation and retains a result until the retained
 * CloudViewModel collector receives it. A channel (rather than replaying SharedFlow)
 * also prevents a rotation from re-applying the same sign-in result.
 */
object DriveAuthEvents {
    private val resultsChannel = Channel<Result<String>>(capacity = Channel.BUFFERED)
    val results: Flow<Result<String>> = resultsChannel.receiveAsFlow()

    fun emit(result: Result<String>) {
        check(resultsChannel.trySend(result).isSuccess) {
            "Drive OAuth result channel is closed; refusing to silently drop sign-in state"
        }
    }
}
