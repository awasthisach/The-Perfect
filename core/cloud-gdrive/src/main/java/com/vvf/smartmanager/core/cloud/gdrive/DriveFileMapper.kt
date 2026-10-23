package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.FileItem
import java.time.Instant

/** One RFC3339 mapping for full listings, change events and backup timestamps. */
fun DriveFileDto.toFileItem(): FileItem = FileItem(
    path = "gdrive://${id.orEmpty()}",
    name = name.orEmpty(),
    sizeBytes = size?.toLongOrNull() ?: 0L,
    lastModified = parseDriveTime(modifiedTime),
    isDirectory = mimeType == "application/vnd.google-apps.folder",
    mimeType = mimeType,
    isFavorite = starred,
    md5Hash = md5Checksum,
    localFileId = id,
    canonicalUri = webViewLink
)

internal fun parseDriveTime(value: String?): Long =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) } ?: 0L
