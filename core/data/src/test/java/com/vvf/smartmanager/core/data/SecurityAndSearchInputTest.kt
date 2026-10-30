package com.vvf.smartmanager.core.data

import com.vvf.smartmanager.core.data.repository.escapeSqlLikeWildcards
import com.vvf.smartmanager.core.data.repository.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndSearchInputTest {
    @Test
    fun likeWildcardsAreEscapedAsLiteralUserInput() {
        assertEquals("a!!!%!_b", escapeSqlLikeWildcards("a!%_b"))
        assertEquals("plain text", escapeSqlLikeWildcards("plain text"))
    }

    @Test
    fun cancellationIsRethrownInsteadOfConvertedToFailure() = runBlocking {
        var cancellationObserved = false
        try {
            runCatchingCancellable<Int> {
                throw CancellationException("test cancellation")
            }
        } catch (_: CancellationException) {
            cancellationObserved = true
        }
        assertTrue("CancellationException must propagate to the caller", cancellationObserved)
    }
}
