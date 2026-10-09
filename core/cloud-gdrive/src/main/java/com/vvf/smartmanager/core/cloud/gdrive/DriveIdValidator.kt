package com.vvf.smartmanager.core.cloud.gdrive

/**
 * Rejects human-readable folder names and malformed resource references before a
 * Drive request is made. "root" is accepted only where a parent folder is expected.
 */
object DriveIdValidator {
    private val resourceIdPattern = Regex("^[A-Za-z0-9_-]{20,128}$")

    fun isValidFileId(value: String?): Boolean =
        value != null && resourceIdPattern.matches(value)

    fun isValidParentId(value: String?): Boolean =
        value == "root" || isValidFileId(value)
}
