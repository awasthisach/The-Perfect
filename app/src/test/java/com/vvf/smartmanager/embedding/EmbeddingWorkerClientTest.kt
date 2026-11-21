package com.vvf.smartmanager.embedding

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the fail-closed preconditions of [EmbeddingWorkerClient].
 * These paths must return [EmbeddingWorkerClient.Result.Blocked] or
 * [EmbeddingWorkerClient.Result.InvalidResponse] without performing network I/O.
 */
class EmbeddingWorkerClientTest {

    @Test
    fun embed_blockedWhenOfflineOnly() = runBlocking {
        val client = EmbeddingWorkerClient(
            baseUrl = "https://example.workers.dev",
            isOfflineOnly = { true },
            isRemoteAiConsentGranted = { true },
        )
        val result = client.embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY)
        assertTrue(result is EmbeddingWorkerClient.Result.Blocked)
        assertEquals(
            EmbeddingWorkerClient.BlockReason.OFFLINE_ONLY,
            (result as EmbeddingWorkerClient.Result.Blocked).reason,
        )
    }

    @Test
    fun embed_blockedWhenConsentMissing() = runBlocking {
        val client = EmbeddingWorkerClient(
            baseUrl = "https://example.workers.dev",
            isOfflineOnly = { false },
            isRemoteAiConsentGranted = { false },
        )
        val result = client.embed(listOf("hello"), EmbeddingWorkerClient.Mode.DOCUMENT)
        assertTrue(result is EmbeddingWorkerClient.Result.Blocked)
        assertEquals(
            EmbeddingWorkerClient.BlockReason.CONSENT_REQUIRED,
            (result as EmbeddingWorkerClient.Result.Blocked).reason,
        )
    }

    @Test
    fun embed_blockedWhenBaseUrlBlank() = runBlocking {
        val client = EmbeddingWorkerClient(
            baseUrl = "   ",
            isOfflineOnly = { false },
            isRemoteAiConsentGranted = { true },
        )
        val result = client.embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY)
        assertTrue(result is EmbeddingWorkerClient.Result.Blocked)
        assertEquals(
            EmbeddingWorkerClient.BlockReason.MISSING_BASE_URL,
            (result as EmbeddingWorkerClient.Result.Blocked).reason,
        )
    }

    @Test
    fun embed_invalidWhenTextsEmpty() = runBlocking {
        val client = EmbeddingWorkerClient(
            baseUrl = "https://example.workers.dev",
            isOfflineOnly = { false },
            isRemoteAiConsentGranted = { true },
        )
        val result = client.embed(emptyList(), EmbeddingWorkerClient.Mode.QUERY)
        assertTrue(result is EmbeddingWorkerClient.Result.InvalidResponse)
    }

    @Test
    fun embed_invalidWhenTooManyTexts() = runBlocking {
        val client = EmbeddingWorkerClient(
            baseUrl = "https://example.workers.dev",
            isOfflineOnly = { false },
            isRemoteAiConsentGranted = { true },
        )
        val texts = List(33) { "item-$it" }
        val result = client.embed(texts, EmbeddingWorkerClient.Mode.DOCUMENT)
        assertTrue(result is EmbeddingWorkerClient.Result.InvalidResponse)
    }
}
