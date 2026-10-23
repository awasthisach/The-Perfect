package com.vvf.smartmanager

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-only App Check provider. Register the emitted debug token in Firebase Console
 * for controlled testing; never use this provider in a release build.
 */
internal object AppCheckProviderInstaller {
    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
