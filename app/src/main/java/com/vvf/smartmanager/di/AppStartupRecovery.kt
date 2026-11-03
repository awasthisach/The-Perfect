package com.vvf.smartmanager.di

import android.content.Context
import android.util.Log
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.domain.restore.impl.LocalRestoreApplier
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import java.io.File

/** Runs synchronously in the database provider, before Room or SQLCipher can be constructed. */
internal object AppStartupRecovery {
    fun prepare(
        context: Context,
        cryptoSecurityManager: CryptoSecurityManager,
        vaultDirectory: File,
        jvmUnitTest: Boolean
    ) {
        // Remove plaintext even if subsequent journal validation fails closed.
        cleanupStalePlaintextBackupStaging(context)
        if (jvmUnitTest) return

        val liveDatabaseFile = context.getDatabasePath(VVFDatabase.DATABASE_NAME)
        // Current journals must recover first: their swap artifacts must not be mistaken
        // for an unjournaled legacy transaction when inspecting the old cache location.
        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDatabaseFile,
            liveVaultDir = vaultDirectory,
            snapshotRoot = File(context.filesDir, "restore-work/snapshots"),
            cryptoSecurityManager = cryptoSecurityManager
        )
        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDatabaseFile,
            liveVaultDir = vaultDirectory,
            snapshotRoot = File(context.cacheDir, "restore-work/snapshots"),
            cryptoSecurityManager = cryptoSecurityManager
        )
    }

    private fun cleanupStalePlaintextBackupStaging(context: Context) {
        context.cacheDir.listFiles().orEmpty()
            .filter { file ->
                file.name.startsWith("archive-staging-") ||
                    (file.name.startsWith("backup-") && file.name.endsWith(".zip"))
            }
            .forEach(::deleteStalePlaintextArtifact)

        // Never remove snapshots here: they may contain the only crash-recovery copy.
        listOf(File(context.filesDir, "restore-work"), File(context.cacheDir, "restore-work"))
            .forEach { restoreRoot ->
                restoreRoot.listFiles().orEmpty()
                    .filter { it.name.startsWith("restore-") || it.name.startsWith("dry-run-") }
                    .forEach(::deleteStalePlaintextArtifact)
            }
    }

    private fun deleteStalePlaintextArtifact(file: File) {
        val removed = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!removed && file.exists()) {
            Log.w("VVFApplication", "Unable to remove stale plaintext backup staging: ${file.name}")
        }
    }
}
