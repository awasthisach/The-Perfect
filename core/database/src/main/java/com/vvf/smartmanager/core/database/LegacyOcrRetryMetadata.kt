package com.vvf.smartmanager.core.database

/**
 * Parsed legacy OCR retry state formerly embedded in the user-visible comma-separated tags.
 * Only a complete token beginning with the reserved prefix is internal; similarly named user
 * tags such as "myocr_retry_after:..." remain ordinary tags.
 */
internal data class LegacyOcrRetryMetadata(
    val visibleTags: String,
    val retryAfterEpochMillis: Long?,
    val retryAttempt: Int,
    val hadInternalMarker: Boolean
)

internal fun parseLegacyOcrRetryMetadata(rawTags: String): LegacyOcrRetryMetadata {
    val tokens = rawTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val markers = tokens.filter { it.startsWith("ocr_retry_after:", ignoreCase = true) }
    val selectedMarker = markers.firstOrNull()
    val parts = selectedMarker?.split(':').orEmpty()
    val retryAfter = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0L }
    val retryAttempt = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    return LegacyOcrRetryMetadata(
        visibleTags = tokens.filterNot { it.startsWith("ocr_retry_after:", ignoreCase = true) }
            .joinToString(","),
        retryAfterEpochMillis = retryAfter,
        retryAttempt = if (retryAfter == null) 0 else retryAttempt,
        hadInternalMarker = markers.isNotEmpty()
    )
}
