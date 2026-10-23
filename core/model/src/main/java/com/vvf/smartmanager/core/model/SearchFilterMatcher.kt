package com.vvf.smartmanager.core.model

/** Shared lexical/neural predicate; preserves existing date, size and category semantics. */
fun SearchFilter.matches(item: FileItem, now: Long = System.currentTimeMillis()): Boolean {
    if (!includeHidden && item.isHidden || !matchesCategory(item, category)) return false
    if (item.sizeBytes !in sizeFilter.minBytes..sizeFilter.maxBytes) return false
    val days = when (dateFilter) {
        DateFilter.ANY -> null
        DateFilter.TODAY -> 1
        DateFilter.LAST_7_DAYS -> 7
        DateFilter.LAST_30_DAYS -> 30
        DateFilter.LAST_YEAR -> 365
    }
    if (days != null && item.lastModified < now - days * 86_400_000L) return false
    return item.tags.map { it.lowercase() }.toSet().containsAll(selectedTags.map { it.lowercase() })
}

private fun matchesCategory(item: FileItem, category: FileCategory): Boolean {
        if (category == FileCategory.ALL) return true
        if (category == FileCategory.FAVORITES) return item.isFavorite
        if (category == FileCategory.TRASH) return item.isTrash
        val ext = item.extension.lowercase()
        val mime = item.mimeType?.lowercase() ?: ""
        return when (category) {
            FileCategory.IMAGES -> mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic")
            FileCategory.VIDEOS -> mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv")
            FileCategory.AUDIO -> mime.startsWith("audio/") || ext in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")
            FileCategory.DOCUMENTS -> mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") || ext in listOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "pptx", "ppt", "csv", "json", "xml", "epub", "md")
            FileCategory.ARCHIVES -> mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") || ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
            FileCategory.APKS -> mime.contains("package-archive") || ext in listOf("apk", "xapk", "apks")
            FileCategory.VAULT -> item.isEncrypted
            else -> true
        }
    }

