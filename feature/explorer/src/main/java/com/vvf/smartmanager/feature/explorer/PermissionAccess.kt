package com.vvf.smartmanager.feature.explorer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/** Fast OS-level storage access probe used to stabilize permission UI. */
object PermissionAccess {
    fun hasBrowseAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                // Target SDK 36 runs in scoped storage on Android 10. READ_EXTERNAL_STORAGE
                // exposes shared media, not unrestricted directory traversal. SAF is not
                // implemented by this Explorer yet, so fail closed instead of claiming access.
                false
            else -> ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
