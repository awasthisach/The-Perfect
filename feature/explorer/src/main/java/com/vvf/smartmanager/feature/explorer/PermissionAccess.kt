package com.vvf.smartmanager.feature.explorer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/** Fast OS-level storage access probe used to stabilize permission UI. */
object PermissionAccess {
    /**
     * Whether the app can list the primary tree. READ_EXTERNAL_STORAGE on Android 10 is
     * intentionally not treated as full-tree access: scoped storage requires SAF for that.
     */
    fun hasBrowseAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Access usable by the category/media views when full-tree access is unavailable. */
    fun hasMediaAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ||
                    hasPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> false
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
