package com.vvf.smartmanager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqlCipherInstrumentedTest {
    @Test fun encryptedDbOpens() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher")
        val crypto = CryptoSecurityManager(ctx)
        val pass = crypto.getOrCreateDatabasePassphrase()
        try {
            val db = VVFDatabase.buildEncryptedDatabase(ctx, pass)
            assertTrue(db.isOpen)
            db.close()
        } finally { crypto.wipeBuffer(pass) }
    }
}
