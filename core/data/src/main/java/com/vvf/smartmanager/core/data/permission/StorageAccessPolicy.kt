package com.vvf.smartmanager.core.data.permission

/**
 * PROD-007: Deterministic storage access policy.
 *
 * Policy justification:
 * - File-manager product requires broad filesystem visibility on primary shared storage.
 * - Android 11+ prefers MANAGE_EXTERNAL_STORAGE for that use case; media-only grants are
 *   insufficient for arbitrary document/archive exploration.
 * - Legacy READ/WRITE external storage applies only on older API levels (maxSdkVersion gated in manifest).
 * - Access is still constrained by StoragePathPolicy fail-closed roots (STORAGE-INV-001).
 */
enum class StorageAccessLevel {
    NONE,
    MEDIA_ONLY,
    LEGACY_FULL,
    ALL_FILES
}

data class StoragePermissionSnapshot(
    val sdkInt: Int,
    val hasManageExternalStorage: Boolean = false,
    val hasReadExternalStorage: Boolean = false,
    val hasWriteExternalStorage: Boolean = false,
    val hasReadMediaImages: Boolean = false,
    val hasReadMediaVideo: Boolean = false,
    val hasReadMediaAudio: Boolean = false
)

data class StorageAccessDecision(
    val level: StorageAccessLevel,
    val canBrowsePrimaryTree: Boolean,
    val canListMediaCategories: Boolean,
    val requiresSettingsAllFilesAccess: Boolean,
    val requiresRuntimeMediaRequest: Boolean,
    val requiresLegacyReadRequest: Boolean,
    val userMessageKey: String
)

object StorageAccessPolicy {
    fun evaluate(snapshot: StoragePermissionSnapshot): StorageAccessDecision {
        val sdk = snapshot.sdkInt
        return when {
            sdk >= 30 && snapshot.hasManageExternalStorage -> decision(
                StorageAccessLevel.ALL_FILES, true, true, false, false, false, "storage_access_granted_all_files"
            )
            sdk >= 33 -> {
                val anyMedia = snapshot.hasReadMediaImages || snapshot.hasReadMediaVideo || snapshot.hasReadMediaAudio
                if (anyMedia) decision(
                    StorageAccessLevel.MEDIA_ONLY, false, true, true, false, false,
                    "storage_access_media_only_full_browse_requires_all_files"
                ) else decision(
                    StorageAccessLevel.NONE, false, false, true, true, false,
                    "storage_access_none_request_media_or_all_files"
                )
            }
            sdk >= 30 -> decision(
                StorageAccessLevel.NONE, false, false, true, false, false, "storage_access_none_request_all_files"
            )
            sdk >= 29 -> {
                if (snapshot.hasReadExternalStorage) decision(
                    StorageAccessLevel.LEGACY_FULL, true, true, false, false, false, "storage_access_granted_legacy_read"
                ) else decision(
                    StorageAccessLevel.NONE, false, false, false, false, true, "storage_access_none_request_legacy_read"
                )
            }
            else -> {
                val ok = snapshot.hasReadExternalStorage || snapshot.hasWriteExternalStorage
                if (ok) decision(
                    StorageAccessLevel.LEGACY_FULL, true, true, false, false, false, "storage_access_granted_legacy_rw"
                ) else decision(
                    StorageAccessLevel.NONE, false, false, false, false, true, "storage_access_none_request_legacy_rw"
                )
            }
        }
    }

    fun assertCanBrowsePrimaryTree(decision: StorageAccessDecision) {
        require(decision.canBrowsePrimaryTree) {
            "Storage browse denied (${decision.level}): ${decision.userMessageKey}"
        }
    }

    fun assertCanListMedia(decision: StorageAccessDecision) {
        require(decision.canListMediaCategories) {
            "Media listing denied (${decision.level}): ${decision.userMessageKey}"
        }
    }

    private fun decision(
        level: StorageAccessLevel,
        browse: Boolean,
        media: Boolean,
        settings: Boolean,
        mediaReq: Boolean,
        legacyReq: Boolean,
        msg: String
    ) = StorageAccessDecision(level, browse, media, settings, mediaReq, legacyReq, msg)
}
