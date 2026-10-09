package com.vvf.smartmanager.core.cloud.gdrive

/**
 * Small deterministic policy helpers so token/session boundary behavior can be unit-tested
 * without making live Google or Firebase requests.
 */
object DriveSessionPolicy {
    const val DEFAULT_ACCESS_TOKEN_MAX_AGE_MS: Long = 55L * 60L * 1000L

    fun isLikelyExpired(
        tokenIssuedAtMs: Long,
        nowMs: Long,
        maxAgeMs: Long = DEFAULT_ACCESS_TOKEN_MAX_AGE_MS
    ): Boolean {
        if (tokenIssuedAtMs <= 0L || maxAgeMs <= 0L || nowMs < tokenIssuedAtMs) return true
        return nowMs - tokenIssuedAtMs >= maxAgeMs
    }

    fun accountsMatch(googleEmail: String?, firebaseEmail: String?): Boolean {
        val google = googleEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
        val firebase = firebaseEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
        return google == firebase
    }
}
