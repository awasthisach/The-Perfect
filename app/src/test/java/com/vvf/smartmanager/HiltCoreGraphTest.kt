package com.vvf.smartmanager

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.EntryPoints
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = VVFApplication::class)
class HiltCoreGraphTest {
    @Test
    fun applicationReceivesOneSingletonGraphAndCanonicalVaultPath() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        // Reinject from the real component without starting another Application or adding
        // test-only entry points to the production graph.
        val anotherTarget = VVFApplication()
        EntryPoints.get(app, VVFApplication_GeneratedInjector::class.java)
            .injectVVFApplication(anotherTarget)

        assertSame(app.cryptoSecurityManager, anotherTarget.cryptoSecurityManager)
        assertSame(app.database, anotherTarget.database)
        assertSame(app.storageManager, anotherTarget.storageManager)
        assertSame(app.fileManagerRepository, anotherTarget.fileManagerRepository)
        assertSame(app.vaultRepository, anotherTarget.vaultRepository)
        assertSame(app.searchRepository, anotherTarget.searchRepository)
        assertSame(app.vaultDirectory, anotherTarget.vaultDirectory)
        assertEquals(File(app.filesDir, "vvf_vault_encrypted").canonicalFile, app.vaultDirectory.canonicalFile)
        assertEquals(app.storageManager.getPrimaryStoragePath(), app.fileManagerRepository.getDefaultStoragePath())
    }

    @Test
    fun jvmGraphUsesUsableInMemoryRoomInsteadOfLoadingNativeSqlCipher() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        assertNull(app.database.openHelper.databaseName)
        app.database.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun mainActivityStartsWithInjectedCoreAndManualUseCases() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        try {
            val activity = controller.setup().get()
            val app = activity.application as VVFApplication
            assertFalse(activity.isFinishing)
            assertNotNull(app.getDirectoryFilesUseCase)
            assertNotNull(app.cloudSyncUseCase)
            assertNotNull(app.vaultRepository)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
