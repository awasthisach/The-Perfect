package com.vvf.smartmanager.core.database

/**
 * Parsed legacy OCR retry state formerly embedded in comma-separated tags.
 *
 * Only the complete historical wire format "ocr_retry_after:<epochMillis>:<attempt>"
 * is internal. Partial, malformed, or similarly named tokens remain user tags.
 */
internal data class LegacyOcrRetryMetadata(
    val visibleTags: String,
    val retryAfterEpochMillis: Long?,
    val retryAttempt: Int,
    val hadInternalMarker: Boolean
)

private data class LegacyOcrRetryMarker(
    val token: String,
    val retryAfterEpochMillis: Long,
    val retryAttempt: Int
)

private const val LEGACY_OCR_RETRY_PREFIX = "ocr_retry_after"
private const val LEGACY_OCR_RETRY_MAX_ATTEMPT = 10

internal fun parseLegacyOcrRetryMetadata(rawTags: String): LegacyOcrRetryMetadata {
    val tokens = rawTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val markers = tokens.mapNotNull { token ->
        val parts = token.split(':')
        if (parts.size != 3 || !parts[0].equals(LEGACY_OCR_RETRY_PREFIX, ignoreCase = true)) {
            return@mapNotNull null
        }
        val retryAfter = parts[1].toLongOrNull()?.takeIf { it >= 0L } ?: return@mapNotNull null
        val attempt = parts[2].toIntOrNull()
            ?.takeIf { it in 1..LEGACY_OCR_RETRY_MAX_ATTEMPT }
            ?: return@mapNotNull null
        LegacyOcrRetryMarker(token, retryAfter, attempt)
    }
    val selected = markers.maxWithOrNull(
        compareBy<LegacyOcrRetryMarker> { it.retryAfterEpochMillis }
            .thenBy { it.retryAttempt }
    )
    val internalTokens = markers.mapTo(mutableSetOf()) { it.token }

    return LegacyOcrRetryMetadata(
        visibleTags = tokens.filterNot { it in internalTokens }.joinToString(","),
        retryAfterEpochMillis = selected?.retryAfterEpochMillis,
        retryAttempt = selected?.retryAttempt ?: 0,
        hadInternalMarker = markers.isNotEmpty()
    )
}
