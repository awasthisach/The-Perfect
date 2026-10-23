package com.vvf.smartmanager.core.security

/** Process-wide maintenance barrier. Leases may finish on another coroutine thread. */
object VaultMaintenance {
    private val monitor = java.lang.Object()
    private var paused = false
    private var operations = 0

    suspend fun <T> operation(block: suspend () -> T): Result<T> = runCatching {
        synchronized(monitor) {
            check(!paused) { "Vault maintenance in progress" }
            operations++
        }
        try { block() } finally {
            synchronized(monitor) { operations--; monitor.notifyAll() }
        }
    }

    fun pause() = synchronized(monitor) {
        check(!paused) { "Vault maintenance already active" }
        paused = true
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        try {
            while (operations != 0) {
                check(System.nanoTime() < deadline) { "Vault operations did not quiesce" }
                monitor.wait(10)
            }
        } catch (error: Throwable) {
            paused = false
            throw error
        }
    }

    fun resume() = synchronized(monitor) { paused = false }
}
