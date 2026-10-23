package com.vvf.smartmanager

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Production attestation provider. Firebase App Check must be registered for this app
 * with the matching release/Play App Signing SHA-256 before native Worker acceptance.
 */
internal object AppCheckProviderInstaller {
    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
