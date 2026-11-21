package com.vvf.smartmanager.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceFileIdentityTest {

    @Test
    fun stableKey_distinguishesDifferentSourcesForSameFileId() {
        val drive = SourceFileIdentity(FileSourceType.GOOGLE_DRIVE, "account-a", "same-id")
        val internal = SourceFileIdentity(FileSourceType.INTERNAL_STORAGE, "primary", "same-id")

        assertNotEquals(drive.stableKey, internal.stableKey)
    }

    @Test
    fun stableKey_distinguishesDifferentSourceInstances() {
        val firstCard = SourceFileIdentity(FileSourceType.REMOVABLE_STORAGE, "volume-a", "document-uri")
        val secondCard = SourceFileIdentity(FileSourceType.REMOVABLE_STORAGE, "volume-b", "document-uri")

        assertNotEquals(firstCard.stableKey, secondCard.stableKey)
    }

    @Test
    fun stableKey_isCollisionSafeWhenIdentifiersContainSeparators() {
        val first = SourceFileIdentity(FileSourceType.INTERNAL_STORAGE, "a:b", "c")
        val second = SourceFileIdentity(FileSourceType.INTERNAL_STORAGE, "a", "b:c")

        assertNotEquals(first.stableKey, second.stableKey)
    }

    @Test
    fun identity_rejectsBlankProviderIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceFileIdentity(FileSourceType.GOOGLE_DRIVE, "account", " ")
        }
    }

    @Test
    fun indexedFile_rejectsMalformedSha256() {
        val identity = SourceFileIdentity(FileSourceType.INTERNAL_STORAGE, "primary", "content://file/1")

        assertThrows(IllegalArgumentException::class.java) {
            IndexedSourceFile(
                identity = identity,
                displayName = "report.pdf",
                sizeBytes = 100,
                mimeType = "application/pdf",
                modifiedAtEpochMillis = 1,
                sha256 = "not-a-sha256"
            )
        }
    }

    @Test
    fun indexedFile_acceptsValidSha256() {
        val identity = SourceFileIdentity(FileSourceType.GOOGLE_DRIVE, "account", "drive-file-id")
        val hash = "a".repeat(64)

        val file = IndexedSourceFile(
            identity = identity,
            displayName = "report.pdf",
            sizeBytes = 100,
            mimeType = "application/pdf",
            modifiedAtEpochMillis = 1,
            sha256 = hash
        )

        assertEquals(hash, file.sha256)
    }
}
