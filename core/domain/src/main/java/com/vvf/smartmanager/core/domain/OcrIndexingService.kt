package com.vvf.smartmanager.core.domain

import android.util.Log
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Event emitted upon successful completion of an OCR scan operation.
 */
data class OcrCompletionEvent(
    val fileItem: FileItem,
    val ocrResult: OcrResult,
    val autoIndexFts: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Domain service that listens for OCR completion events and provides mechanisms to index
 * extracted text, tags, and salient keywords directly into the Core Search Full-Text Search (FTS4) database.
 *
 * Ensures:
 * - Decoupled reactive event distribution via [SharedFlow]
 * - Automatic background FTS indexation without blocking UI threads
 * - Salient keyword extraction with stopword removal to optimize index size
 * - Safe sanitization to prevent SQLite FTS query syntax corruption
 */
class OcrIndexingService(
    private val searchRepository: SearchRepository,
    private val indexOcrTextUseCase: IndexOcrTextUseCase = IndexOcrTextUseCase(searchRepository)
) {

    private val _events = MutableSharedFlow<OcrCompletionEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val completionEvents: SharedFlow<OcrCompletionEvent> = _events.asSharedFlow()

    private var listeningJob: Job? = null

    /**
     * Publishes an OCR completion event to all active subscribers.
     */
    fun notifyOcrCompleted(event: OcrCompletionEvent) {
        _events.tryEmit(event)
    }

    /**
     * Helper overload to publish an OCR completion event directly from parameters.
     */
    fun notifyOcrCompleted(
        fileItem: FileItem,
        ocrResult: OcrResult,
        autoIndexFts: Boolean = true
    ) {
        notifyOcrCompleted(
            OcrCompletionEvent(
                fileItem = fileItem,
                ocrResult = ocrResult,
                autoIndexFts = autoIndexFts
            )
        )
    }

    /**
     * Starts a coroutine listener that consumes [OcrCompletionEvent] streams
     * and automatically indexes extracted text into the FTS database.
     *
     * @param coroutineScope The CoroutineScope to bind the event collector job to.
     * @return The active [Job] handling event listening.
     */
    fun startListening(coroutineScope: CoroutineScope): Job {
        listeningJob?.cancel()
        val job = coroutineScope.launch(Dispatchers.IO) {
            try {
                completionEvents.collect { event ->
                    if (event.autoIndexFts && event.ocrResult.fullText.isNotBlank()) {
                        try {
                            indexOcrResult(event.fileItem, event.ocrResult)
                            Log.d(TAG, "Successfully indexed OCR text for ${event.fileItem.name} into FTS.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to auto-index OCR text for ${event.fileItem.path}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in OCR completion event stream", e)
            }
        }
        listeningJob = job
        return job
    }

    /**
     * Stops the background event listener job if active.
     */
    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
    }

    /**
     * Indexes the given [OcrResult] into the FTS database by extracting salient keywords
     * and attaching the "#ocr" tag to the file metadata.
     *
     * @param fileItem The file that was processed.
     * @param ocrResult Extracted OCR result containing full text and confidence blocks.
     * @param maxKeywords Maximum number of distinct salient keywords to index as searchable tags.
     */
    suspend fun indexOcrResult(
        fileItem: FileItem,
        ocrResult: OcrResult,
        maxKeywords: Int = 10
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (ocrResult.fullText.isBlank()) {
                return@withContext Result.success(true)
            }

            // Delegate to IndexOcrTextUseCase with custom max keywords
            indexOcrTextUseCase(fileItem, ocrResult, maxKeywords)
        } catch (e: Exception) {
            Log.e(TAG, "Failed indexing OCR result into FTS for ${fileItem.path}", e)
            Result.failure(e)
        }
    }

    /**
     * Indexes arbitrary extracted text and optional custom keywords into the FTS database.
     */
    suspend fun indexExtractedText(
        fileItem: FileItem,
        rawText: String,
        extraTags: List<String> = emptyList()
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (rawText.isBlank() && extraTags.isEmpty()) {
                return@withContext Result.success(true)
            }

            // Always add foundational #ocr tag
            searchRepository.addTagToFile(fileItem.path, "ocr")

            // Add user-provided extra tags
            for (tag in extraTags) {
                if (tag.isNotBlank()) {
                    searchRepository.addTagToFile(fileItem.path, tag.trim().lowercase(Locale.getDefault()))
                }
            }

            // Extract and index salient keywords
            val keywords = extractSalientKeywords(rawText, maxKeywords = 10)
            for (kw in keywords) {
                searchRepository.addTagToFile(fileItem.path, kw)
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed indexing extracted text for ${fileItem.path}", e)
            Result.failure(e)
        }
    }

    /**
     * Removes the #ocr tag and associated indexed tags from the file.
     */
    suspend fun removeOcrIndex(fileItem: FileItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            searchRepository.removeTagFromFile(fileItem.path, "ocr")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Natural Language keyword extractor: filters common stopwords, non-alphanumeric symbols,
     * short tokens (<4 chars), and returns deduplicated top keywords.
     */
    private fun extractSalientKeywords(text: String, maxKeywords: Int): List<String> {
        val stopwords = setOf(
            "the", "and", "for", "with", "this", "that", "from", "have", "been",
            "page", "date", "name", "total", "your", "will", "they", "what", "which",
            "about", "into", "more", "other", "some", "such", "than", "them", "then",
            "these", "there", "were", "when", "where", "would", "also", "could", "should"
        )

        return text
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length in 4..25 && !stopwords.contains(it) }
            .distinct()
            .take(maxKeywords)
    }

    companion object {
        private const val TAG = "OcrIndexingService"
    }
}
