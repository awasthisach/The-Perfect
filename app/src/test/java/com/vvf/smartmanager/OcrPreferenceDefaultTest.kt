package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.security.VaultBiometricCryptoHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class OcrPreferenceDefaultTest {
    @Test
    fun automaticOcrIsOptInAndTheChoicePersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertFalse("OCR must be off until the user opts in", VaultBiometricCryptoHelper.isAutoIndexOcrEnabled(context))

        VaultBiometricCryptoHelper.setAutoIndexOcrEnabled(context, true)
        assertTrue(VaultBiometricCryptoHelper.isAutoIndexOcrEnabled(context))

        VaultBiometricCryptoHelper.setAutoIndexOcrEnabled(context, false)
        assertFalse(VaultBiometricCryptoHelper.isAutoIndexOcrEnabled(context))
    }
}
