package com.vvf.smartmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped barrier for database and vault recovery.
 *
 * The application owns the scope and starts this exactly once. Awaiters are independently
 * cancellable: cancelling a screen or worker never cancels the shared initialization job.
 * A failed or cancelled initialization is terminal for this process; a new process gets a
 * fresh instance and repeats the idempotent journal recovery.
 */
sealed interface StartupState {
    data object Initializing : StartupState
    data object Recovering : StartupState
    data object Ready : StartupState
    data class Failed(val reason: String) : StartupState
}

class StartupReadiness {
    private val lock = Any()
    private val mutableState = MutableStateFlow<StartupState>(StartupState.Initializing)
    private val completion = CompletableDeferred<Unit>()
    private var initializationJob: Job? = null

    val state: StateFlow<StartupState> = mutableState.asStateFlow()

    fun start(
        scope: CoroutineScope,
        initialize: suspend (markRecovering: () -> Unit) -> Unit
    ): Job = synchronized(lock) {
        initializationJob?.let { return@synchronized it }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                initialize { mutableState.value = StartupState.Recovering }
                mutableState.value = StartupState.Ready
                completion.complete(Unit)
            } catch (cancelled: CancellationException) {
                mutableState.value = StartupState.Failed("Startup initialization was cancelled")
                completion.cancel(cancelled)
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = StartupState.Failed(
                    error.message?.takeIf(String::isNotBlank) ?: "Secure startup initialization failed"
                )
                completion.completeExceptionally(error)
            }
        }
        initializationJob = job
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException && !completion.isCompleted) {
                mutableState.value = StartupState.Failed("Startup initialization was cancelled")
                completion.cancel(cause)
            }
        }
        job.start()
        job
    }

    suspend fun awaitReady() {
        completion.await()
    }

    fun isReady(): Boolean = mutableState.value == StartupState.Ready
}
