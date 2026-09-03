package com.vvf.smartmanager.core.data.permission

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Runtime bridge for [StorageAccessPolicy]. Reads OS state once per evaluation;
 * does not cache grants across process death — callers re-evaluate after permission flows.
 */
class StoragePermissionGate(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) {
    fun snapshot(): StoragePermissionSnapshot {
        return StoragePermissionSnapshot(
            sdkInt = sdkInt,
            hasManageExternalStorage = queryManageExternalStorage(),
            hasReadExternalStorage = granted(Manifest.permission.READ_EXTERNAL_STORAGE),
            hasWriteExternalStorage = granted(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            hasReadMediaImages = if (sdkInt >= 33) granted(Manifest.permission.READ_MEDIA_IMAGES) else false,
            hasReadMediaVideo = if (sdkInt >= 33) granted(Manifest.permission.READ_MEDIA_VIDEO) else false,
            hasReadMediaAudio = if (sdkInt >= 33) granted(Manifest.permission.READ_MEDIA_AUDIO) else false
        )
    }

    fun evaluate(): StorageAccessDecision = StorageAccessPolicy.evaluate(snapshot())

    fun requireBrowsePrimaryTree(): StorageAccessDecision {
        val decision = evaluate()
        StorageAccessPolicy.assertCanBrowsePrimaryTree(decision)
        return decision
    }

    fun requireListMedia(): StorageAccessDecision {
        val decision = evaluate()
        StorageAccessPolicy.assertCanListMedia(decision)
        return decision
    }

    /**
     * [Environment.isExternalStorageManager] is API 30+. [sdkInt] is injectable for unit tests,
     * so the guard uses that field; lint cannot prove it tracks the OS API level, hence SuppressLint.
     */
    @SuppressLint("NewApi")
    private fun queryManageExternalStorage(): Boolean {
        if (sdkInt < Build.VERSION_CODES.R) return false
        return Environment.isExternalStorageManager()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
