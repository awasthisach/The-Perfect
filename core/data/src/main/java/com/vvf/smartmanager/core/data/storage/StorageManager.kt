package com.vvf.smartmanager.core.data.storage

import android.content.Context
import com.vvf.smartmanager.core.database.dao.FileDao

/**
 * Production storage manager. Fail-closed authorization (STORAGE-INV-001 / PROD-001).
 * Listing never seeds demo files (STORAGE-INV-002 / PROD-004).
 */
class StorageManager(
    context: Context,
    fileDao: FileDao
) : StorageManagerOps(context, fileDao)
