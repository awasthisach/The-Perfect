package com.vvf.smartmanager

import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class IndexingOutcomeMappingTest {

    @Test
    fun cancellationIsRethrownInsteadOfBecomingRetryOrPermanentFailure() = runBlocking {
        val expected = CancellationException("indexing cancelled")
        try {
            mapFileIndexingOutcome { throw expected }
            throw AssertionError("Expected cancellation to propagate")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }

    @Test
    fun storagePermissionFailureMapsToPermissionRequired() = runBlocking {
        val result = mapFileIndexingOutcome {
            throw SecurityException("permission revoked")
        }
        assertEquals(FileIndexingOutcome.PermissionRequired("permission revoked"), result)
    }

    @Test
    fun storageIoFailureMapsToRetryableFailure() = runBlocking {
        val result = mapFileIndexingOutcome {
            throw IOException("media provider unavailable")
        }
        assertEquals(FileIndexingOutcome.RetryableFailure("media provider unavailable"), result)
    }

    @Test
    fun unexpectedFailureMapsToPermanentAndIsReported() = runBlocking {
        val expected = IllegalStateException("unexpected")
        var reported: Throwable? = null
        val result = mapFileIndexingOutcome(onUnexpectedError = { reported = it }) {
            throw expected
        }
        assertEquals(FileIndexingOutcome.PermanentFailure("unexpected"), result)
        assertSame(expected, reported)
    }

    @Test
    fun successfulIndexerOutcomePassesThroughUnchanged() = runBlocking {
        val expected = FileIndexingOutcome.Completed(12)
        val result = mapFileIndexingOutcome { expected }
        assertTrue(result === expected)
    }
}
