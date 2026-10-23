package com.vvf.smartmanager.core.data.repository

import android.system.Os
import java.io.File
import java.io.IOException

/** API 24-compatible publication: never replace a destination created by another writer. */
internal class AtomicNoClobberPublisher(
    private val link: (String, String) -> Unit = Os::link,
    private val unlink: (String) -> Unit = Os::remove,
    private val discardUnpublished: (File) -> Unit
) {
    fun publish(temp: File, target: File) {
        var linked = false
        try {
            link(temp.absolutePath, target.absolutePath)
            linked = true
            unlink(temp.absolutePath)
        } catch (error: Exception) {
            if (linked) {
                // Both paths reference the published inode. Never shred that inode.
                try {
                    unlink(temp.absolutePath)
                    return
                } catch (retry: Exception) {
                    error.addSuppressed(retry)
                    throw SharedPlaintextLinkCleanupException("Published file retained; temporary link cleanup failed", error)
                }
            }
            discardUnpublished(temp)
            // Reserve-then-rename is not a safe fallback: another writer can replace
            // the reservation before rename. Unsupported hard-link filesystems must
            // fail closed until a platform atomic no-replace primitive is available.
            throw IOException("Destination does not permit atomic publication without replacement; choose a supported location", error)
        }
    }
}

internal class SharedPlaintextLinkCleanupException(message: String, cause: Throwable) : IOException(message, cause)
