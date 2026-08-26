package com.vvf.smartmanager.core.model

/**
 * Filter by file size bracket.
 */
enum class SizeFilter(val displayName: String, val minBytes: Long, val maxBytes: Long) {
    ANY("Any Size", 0L, Long.MAX_VALUE),
    TINY("< 1 MB", 0L, 1024L * 1024L),
    SMALL("1 MB - 10 MB", 1024L * 1024L, 10L * 1024L * 1024L),
    MEDIUM("10 MB - 100 MB", 10L * 1024L * 1024L, 100L * 1024L * 1024L),
    LARGE("100 MB - 1 GB", 100L * 1024L * 1024L, 1024L * 1024L * 1024L),
    HUGE("> 1 GB", 1024L * 1024L * 1024L, Long.MAX_VALUE)
}

/**
 * Filter by date last modified.
 */
enum class DateFilter(val displayName: String) {
    ANY("Any Time"),
    TODAY("Today (Last 24h)"),
    LAST_7_DAYS("Past 7 Days"),
    LAST_30_DAYS("Past 30 Days"),
    LAST_YEAR("Past Year")
}

/**
 * Origin match classification for the search result.
 */
enum class SearchMatchType(val displayName: String) {
    FILENAME("Filename Match"),
    TAG("Tag Match"),
    METADATA("Metadata Match"),
    FTS("FTS Full-Text Index")
}

/**
 * Multi-dimensional search filters for offline Core Search.
 */
data class SearchFilter(
    val category: FileCategory = FileCategory.ALL,
    val sizeFilter: SizeFilter = SizeFilter.ANY,
    val dateFilter: DateFilter = DateFilter.ANY,
    val selectedTags: Set<String> = emptySet(),
    val sortOption: FileSortOption = FileSortOption.DATE_DESC,
    val includeHidden: Boolean = false
) {
    val isDefault: Boolean
        get() = category == FileCategory.ALL &&
                sizeFilter == SizeFilter.ANY &&
                dateFilter == DateFilter.ANY &&
                selectedTags.isEmpty() &&
                sortOption == FileSortOption.DATE_DESC &&
                !includeHidden

    val activeFilterCount: Int
        get() {
            var count = 0
            if (category != FileCategory.ALL) count++
            if (sizeFilter != SizeFilter.ANY) count++
            if (dateFilter != DateFilter.ANY) count++
            if (selectedTags.isNotEmpty()) count += selectedTags.size
            if (sortOption != FileSortOption.DATE_DESC) count++
            return count
        }
}

/**
 * Search result item decorated with match context.
 */
data class SearchResultItem(
    val fileItem: FileItem,
    val matchType: SearchMatchType,
    val matchedSnippet: String? = null
)
