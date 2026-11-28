package com.vvf.smartmanager.core.data.storage

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes primary-storage index reconciliation with destructive vault source removal.
 * The lock is process-local; after process death no in-memory scan snapshot can survive.
 */
object PrimaryStorageMutationLock {
    val mutex = Mutex()
}
