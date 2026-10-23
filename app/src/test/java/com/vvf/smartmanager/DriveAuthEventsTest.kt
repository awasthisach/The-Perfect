package com.vvf.smartmanager

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveAuthEventsTest {
    @Test
    fun resultEmittedBeforeCollectorStartsIsDeliveredOnce() = runTest {
        DriveAuthEvents.emit(Result.success("test-drive-token"))

        val delivered = withTimeout(1_000) {
            DriveAuthEvents.results.first()
        }

        assertEquals("test-drive-token", delivered.getOrThrow())
    }
}
