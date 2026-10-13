package com.vvf.smartmanager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.plugin.semanticsearch.EmbeddingServiceException
import com.vvf.smartmanager.plugin.semanticsearch.SemanticSearchPluginImpl
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resumable, consent-gated vector indexing. Each run processes at most 50 indexed rows, sends
 * batches within the Worker limit of 32 texts, persists each completed batch to SQLCipher Room,
 * then schedules the next stable keyset page. It never stores tokens in WorkManager input data.
 */
class SemanticEmbeddingIndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VVFApplication ?: return Result.failure()
        if (!app.isEmbeddingConsentEnabled()) return Result.success()
        val plugin = app.semanticSearchPlugin as? SemanticSearchPluginImpl ?: return Result.failure()
        if (!plugin.isModelReady()) return Result.success()

        val afterId = inputData.getLong(KEY_AFTER_ID, 0L).coerceAtLeast(0L)
        return try {
            val page = app.database.fileDao().getSemanticIndexPage(afterId, PAGE_SIZE)
            if (page.isEmpty()) return Result.success()
            if (!app.isEmbeddingConsentEnabled()) return Result.success()

            val candidates = page.map { row ->
                val tags = row.tags.split(',').map(String::trim).filter(String::isNotEmpty)
                val item = FileItem(
                    path = row.path,
                    name = row.name,
                    sizeBytes = row.sizeBytes,
                    lastModified = row.modifiedDate,
                    isDirectory = row.isDirectory,
                    mimeType = row.mimeType,
                    isFavorite = row.isFavorite,
                    isTrash = row.isTrash,
                    originalPath = row.originalPath,
                    deletedTimestamp = row.deletedTimestamp,
                    md5Hash = row.md5Hash,
                    tags = tags,
                    canonicalUri = row.canonicalUri,
                    indexedText = row.contentText.take(MAX_INDEXED_TEXT_CHARS)
                )
                val content = buildString {
                    append(item.name)
                    if (item.tags.isNotEmpty()) append(' ').append(item.tags.joinToString(" "))
                    if (item.indexedText.isNotBlank()) append(' ').append(item.indexedText)
                }.take(MAX_TEXT_CHARS)
                SemanticCandidate(item, content)
            }

            plugin.indexCandidates(candidates)
            if (!app.isEmbeddingConsentEnabled()) return Result.success()

            if (page.size == PAGE_SIZE) {
                enqueueNextPage(afterId = page.last().id)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: EmbeddingServiceException) {
            when {
                error.statusCode == 401 || error.statusCode == 403 -> Result.failure()
                error.statusCode == 429 || error.statusCode >= 500 -> Result.retry()
                else -> Result.failure()
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun enqueueNextPage(afterId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<SemanticEmbeddingIndexWorker>()
            .setInputData(workDataOf(KEY_AFTER_ID to afterId))
            .setConstraints(constraints)
            .setInitialDelay(PAGE_DELAY_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "$UNIQUE_WORK_PREFIX$afterId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val WORK_TAG = "semantic_embedding_index"
        private const val KEY_AFTER_ID = "semantic_embedding_after_id"
        private const val UNIQUE_WORK_PREFIX = "semantic_embedding_index_after_"
        private const val PAGE_SIZE = 50
        private const val PAGE_DELAY_SECONDS = 15L
        private const val MAX_INDEXED_TEXT_CHARS = 4_000
        private const val MAX_TEXT_CHARS = 8_000

        fun initialRequest() = OneTimeWorkRequestBuilder<SemanticEmbeddingIndexWorker>()
            .setInputData(workDataOf(KEY_AFTER_ID to 0L))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()

        const val INITIAL_UNIQUE_WORK = "semantic_embedding_index_initial"
    }
}
