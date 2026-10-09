package com.vvf.smartmanager.core.cloud.gdrive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSessionPolicyTest {
    @Test
    fun accessTokenExpiryBoundaryIsDeterministic() {
        val issuedAt = 1_000_000L
        assertFalse(DriveSessionPolicy.isLikelyExpired(issuedAt, issuedAt + 54 * 60 * 1000L))
        assertTrue(DriveSessionPolicy.isLikelyExpired(issuedAt, issuedAt + 55 * 60 * 1000L))
        assertTrue(DriveSessionPolicy.isLikelyExpired(0L, issuedAt))
        assertTrue(DriveSessionPolicy.isLikelyExpired(issuedAt, issuedAt - 1L))
    }

    @Test
    fun accountAlignmentNormalizesCaseAndWhitespace() {
        assertTrue(DriveSessionPolicy.accountsMatch(" User@Example.com ", "user@example.com"))
        assertFalse(DriveSessionPolicy.accountsMatch("drive@example.com", "firebase@example.com"))
        assertFalse(DriveSessionPolicy.accountsMatch("", "firebase@example.com"))
        assertFalse(DriveSessionPolicy.accountsMatch("drive@example.com", null))
    }
}
