package com.vvf.smartmanager.di

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.domain.restore.impl.LocalRestoreApplier
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppStartupRecoveryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var crypto: CryptoSecurityManager
    private lateinit var vaultDirectory: File

    @Before
    fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = temp.newFolder("files")
        val cache = temp.newFolder("cache")
        val databases = temp.newFolder("databases")
        context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
            override fun getDatabasePath(name: String): File = File(databases, name)
        }
        crypto = CryptoSecurityManager(context, "HiltStartupRecoveryTest")
        vaultDirectory = File(files, "vvf_vault_encrypted")
    }

    @Test
    fun jvmStartupCleansPlaintextButPreservesRollbackMaterial() {
        val archiveStage = File(context.cacheDir, "archive-staging-orphan").apply { mkdirs() }
        File(archiveStage, "secret.txt").writeText("plaintext")
        val oldBackup = File(context.cacheDir, "backup-orphan.zip").apply { writeText("plaintext") }
        val keep = File(context.cacheDir, "unrelated.zip").apply { writeText("keep") }
        val snapshots = mutableListOf<File>()
        val transient = mutableListOf<File>()
        listOf(context.filesDir, context.cacheDir).forEach { parent ->
            val restoreRoot = File(parent, "restore-work").apply { mkdirs() }
            listOf("restore-orphan", "dry-run-orphan").forEach { name ->
                transient += File(restoreRoot, name).apply {
                    mkdirs()
                    File(this, "plaintext.txt").writeText("secret")
                }
            }
            snapshots += File(restoreRoot, "snapshots/rollback.bin").apply {
                parentFile!!.mkdirs()
                writeText("only rollback copy")
            }
        }

        AppStartupRecovery.prepare(context, crypto, vaultDirectory, jvmUnitTest = true)

        assertFalse(archiveStage.exists())
        assertFalse(oldBackup.exists())
        transient.forEach { assertFalse(it.exists()) }
        snapshots.forEach { assertTrue(it.isFile) }
        assertTrue(keep.isFile)
    }

    @Test
    fun currentJournalRecoversBeforeLegacyPathAndRestoresDatabaseVaultAndKey() {
        runBlocking {
            val liveDatabase = context.getDatabasePath(VVFDatabase.DATABASE_NAME)
                .apply { writeText("ORIGINAL-DB") }
            vaultDirectory.mkdirs()
            File(vaultDirectory, "item.vvf").writeText("ORIGINAL-VAULT")
            val currentSnapshots = File(context.filesDir, "restore-work/snapshots")
            val legacySnapshots = File(context.cacheDir, "restore-work/snapshots").apply { mkdirs() }
            val originalPassphrase = crypto.getOrCreateDatabasePassphrase()
            val replacement = ByteArray(32) { (it + 11).toByte() }
            try {
                LocalRestoreApplier(
                    liveDatabaseFile = liveDatabase,
                    liveVaultDir = vaultDirectory,
                    snapshotRoot = currentSnapshots,
                    cryptoSecurityManager = crypto
                ).prepare().getOrThrow()
                liveDatabase.writeText("PARTIAL-DB")
                File(vaultDirectory, "item.vvf").writeText("PARTIAL-VAULT")
                crypto.replaceDatabasePassphraseForRestore(replacement)
                // Legacy-first recovery would reject this as an unjournaled interrupted swap.
                File(liveDatabase.parentFile, liveDatabase.name + ".restore-bak").writeText("swap artifact")

                AppStartupRecovery.prepare(context, crypto, vaultDirectory, jvmUnitTest = false)

                assertEquals("ORIGINAL-DB", liveDatabase.readText())
                assertEquals("ORIGINAL-VAULT", File(vaultDirectory, "item.vvf").readText())
                val recoveredPassphrase = crypto.getOrCreateDatabasePassphrase()
                try {
                    assertArrayEquals(originalPassphrase, recoveredPassphrase)
                } finally {
                    recoveredPassphrase.fill(0)
                }
                assertTrue(currentSnapshots.listFiles().orEmpty().isEmpty())
                assertTrue(legacySnapshots.listFiles().orEmpty().isEmpty())
                assertFalse(File(liveDatabase.parentFile, liveDatabase.name + ".restore-bak").exists())
            } finally {
                originalPassphrase.fill(0)
                replacement.fill(0)
            }
        }
    }

    @Test
    fun invalidJournalFailsClosedAfterPlaintextCleanupWithoutMutatingLiveData() {
        val liveDatabase = context.getDatabasePath(VVFDatabase.DATABASE_NAME)
            .apply { writeText("LIVE-DB") }
        val snapshots = File(context.filesDir, "restore-work/snapshots").apply { mkdirs() }
        File(snapshots, "active-restore.properties").writeText("invalid-journal=true")
        val stage = File(context.filesDir, "restore-work/restore-orphan").apply { mkdirs() }
        File(stage, "plaintext.txt").writeText("secret")

        assertThrows(IllegalStateException::class.java) {
            AppStartupRecovery.prepare(context, crypto, vaultDirectory, jvmUnitTest = false)
        }

        assertFalse(stage.exists())
        assertEquals("LIVE-DB", liveDatabase.readText())
        assertTrue(File(snapshots, "active-restore.properties").isFile)
    }
}
