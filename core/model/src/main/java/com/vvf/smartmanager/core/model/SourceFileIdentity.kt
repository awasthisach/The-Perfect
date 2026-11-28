package com.vvf.smartmanager.core.model

/**
 * Identifies where an indexed file comes from.
 *
 * A display path is not a stable identity: files can be renamed, mounted under a new path,
 * or exist in multiple providers. Duplicate matching must compare content across these
 * source identities without conflating two different source instances.
 */
enum class FileSourceType {
    INTERNAL_STORAGE,
    REMOVABLE_STORAGE,
    GOOGLE_DRIVE
}

/**
 * Stable, source-aware identity for one file.
 *
 * [sourceInstanceId] distinguishes, for example, two SD cards or two Google accounts.
 * [sourceFileId] is provider-specific: a canonical SAF document URI for local/removable
 * storage, or the Drive file ID for Google Drive.
 */
data class SourceFileIdentity(
    val sourceType: FileSourceType,
    val sourceInstanceId: String,
    val sourceFileId: String
) {
    init {
        require(sourceInstanceId.isNotBlank()) { "sourceInstanceId must not be blank" }
        require(sourceFileId.isNotBlank()) { "sourceFileId must not be blank" }
    }

    /**
     * Length-prefixed components avoid collisions caused by user/provider IDs containing
     * separator characters. This key is suitable for a database unique index.
     */
    val stableKey: String
        get() = listOf(sourceType.name, sourceInstanceId, sourceFileId)
            .joinToString(separator = "") { component -> "${component.length}:$component" }
}

/** Availability of a source item without deleting its indexed history. */
enum class IndexedSourceAccessState {
    AVAILABLE,
    TEMPORARILY_UNAVAILABLE,
    PERMISSION_REQUIRED,
    SOURCE_REMOVED,
    ACCESS_REVOKED,
    UNSUPPORTED
}

/**
 * Source-neutral metadata passed from a storage adapter into the unified index.
 *
 * This model intentionally contains no file mutation operation. It describes an indexed
 * item only; scanning and duplicate discovery must never modify source files.
 */
data class IndexedSourceFile(
    val identity: SourceFileIdentity,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val modifiedAtEpochMillis: Long,
    val accessState: IndexedSourceAccessState = IndexedSourceAccessState.AVAILABLE,
    val sha256: String? = null
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(sizeBytes >= 0) { "sizeBytes must not be negative" }
        require(modifiedAtEpochMillis >= 0) { "modifiedAtEpochMillis must not be negative" }
        require(sha256 == null || sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "sha256 must contain exactly 64 hexadecimal characters"
        }
    }
}
