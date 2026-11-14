package com.vvf.smartmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrBatchBootstrapTest {
    @Test
    fun retryDelayUsesExponentialBackoffAndCapsAtSevenDays() {
        val hour = 60L * 60L * 1000L
        assertEquals(6L * hour, OcrBatchBootstrap.calculateOcrRetryDelayMillis(1))
        assertEquals(12L * hour, OcrBatchBootstrap.calculateOcrRetryDelayMillis(2))
        assertEquals(24L * hour, OcrBatchBootstrap.calculateOcrRetryDelayMillis(3))
        assertEquals(7L * 24L * hour, OcrBatchBootstrap.calculateOcrRetryDelayMillis(10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun retryDelayRejectsZeroAttempt() {
        OcrBatchBootstrap.calculateOcrRetryDelayMillis(0)
    }
}
