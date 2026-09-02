package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.AppliedRestore
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.RestoreApplier
import com.vvf.smartmanager.core.domain.restore.RestoreException
import com.vvf.smartmanager.core.domain.restore.RestoreSnapshot
import java.io.File
import java.util.UUID

/** Snapshot + apply + rollback for live database and vault directories. */
class LocalRestoreApplier(
    private val liveDatabaseFile: File,
    private val liveVaultDir: File,
    private val snapshotRoot: File
) : RestoreApplier {
    override suspend fun prepare(): Result<RestoreSnapshot> {
        return try {
            if (!snapshotRoot.exists() && !snapshotRoot.mkdirs()) {
                return Result.failure(RestoreException("Unable to create snapshot root"))
            }
            val token = UUID.randomUUID().toString()
            val dbSnap = File(snapshotRoot, "$token-db")
            val vaultSnap = File(snapshotRoot, "$token-vault")
            if (liveDatabaseFile.exists()) liveDatabaseFile.copyTo(dbSnap, overwrite = true)
            if (liveVaultDir.exists()) liveVaultDir.copyRecursively(vaultSnap, overwrite = true)
            else vaultSnap.mkdirs()
            Result.success(RestoreSnapshot(token, System.currentTimeMillis(), dbSnap, vaultSnap))
        } catch (error: Throwable) {
            Result.failure(RestoreException("Restore snapshot prepare failed", error))
        }
    }

    override suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore> {
        return try {
            liveDatabaseFile.parentFile?.mkdirs()
            if (decryptedBackup.databaseFile.isFile) {
                decryptedBackup.databaseFile.copyTo(liveDatabaseFile, overwrite = true)
            }
            if (liveVaultDir.exists()) liveVaultDir.deleteRecursively()
            liveVaultDir.mkdirs()
            if (decryptedBackup.vaultDir.exists()) {
                decryptedBackup.vaultDir.copyRecursively(liveVaultDir, overwrite = true)
            }
            val fileCount = decryptedBackup.stagingDir.walkTopDown().count { it.isFile }
            val vaultCount = decryptedBackup.vaultDir.walkTopDown().count { it.isFile }
            Result.success(AppliedRestore(true, fileCount, vaultCount, "Restore applied"))
        } catch (error: Throwable) {
            Result.failure(RestoreException("Restore apply failed", error))
        }
    }

    override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> {
        return try {
            if (snapshot.databaseSnapshotPath.isFile) {
                snapshot.databaseSnapshotPath.copyTo(liveDatabaseFile, overwrite = true)
            }
            if (liveVaultDir.exists()) liveVaultDir.deleteRecursively()
            liveVaultDir.mkdirs()
            if (snapshot.vaultSnapshotPath.exists()) {
                snapshot.vaultSnapshotPath.copyRecursively(liveVaultDir, overwrite = true)
            }
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(RestoreException("Restore rollback failed", error))
        }
    }
}
