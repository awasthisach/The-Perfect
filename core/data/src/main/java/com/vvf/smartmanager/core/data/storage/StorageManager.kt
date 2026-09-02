package com.vvf.smartmanager.core.data.storage

import android.content.Context
import com.vvf.smartmanager.core.database.dao.FileDao

/**
 * Production storage manager. Authorization is fail-closed (STORAGE-INV-001 / PROD-001).
 * Listing never seeds demo files (STORAGE-INV-002 / PROD-004).
 *
 * Implementation lives in [StorageManagerImpl]; this facade preserves the historical type name
 * used across the app and feature modules.
 */
class StorageManager(
    context: Context,
    fileDao: FileDao
) : StorageManagerImpl(context, fileDao)
