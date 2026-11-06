package com.vvf.smartmanager.core.domain.restore

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Orchestrates restore as a fail-closed transaction:
 * download -> verify -> decrypt into isolated staging -> prepare snapshot -> apply -> rollback on failure.
 */
class FailClosedRestorePipeline(
    private val workingDir: File,
    private val downloader: BackupDownloader,
    private val verifier: BackupVerifier,
    private val decryptor: BackupDecryptor,
    private val applier: RestoreApplier
) : RestorePipeline {

    private val preparedSnapshots = ConcurrentHashMap<String, RestoreSnapshot>()

    override suspend fun restore(
        remoteBackupId: String,
        expectedChecksum: String?,
        passphrase: CharArray?
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        restoreOnIo(remoteBackupId, expectedChecksum, passphrase)
    }

    private suspend fun restoreOnIo(
        remoteBackupId: String,
        expectedChecksum: String?,
        passphrase: CharArray?
    ): Result<RestoreResult> {
        if (remoteBackupId.isBlank()) {
            passphrase?.fill(0.toChar())
            return Result.failure(VerificationFailedException("Remote backup id is blank"))
        }

        val token = UUID.randomUUID().toString()
        val stagingDir = File(workingDir, "restore-$token")
        var snapshot: RestoreSnapshot? = null
        return try {
            requireWorkingDirectory(stagingDir)

            val downloaded = downloader.download(remoteBackupId).getOrElse { error ->
                if (error is CancellationException) throw error
                return Result.failure(error)
            }
            val verified = verifier.verify(downloaded, expectedChecksum).getOrElse { error ->
                if (error is CancellationException) throw error
                return Result.failure(error)
            }
            val decrypted = decryptor.decrypt(verified, stagingDir, passphrase).getOrElse { error ->
                if (error is CancellationException) throw error
                return Result.failure(error)
            }

            snapshot = applier.prepare().getOrElse { error ->
                if (error is CancellationException) throw error
                return Result.failure(error)
            }
            preparedSnapshots[snapshot!!.token] = snapshot!!

            val applied = applier.apply(decrypted).getOrElse { error ->
                if (error is CancellationException) throw error
                val rollbackResult = applier.rollback(snapshot!!)
                preparedSnapshots.remove(snapshot!!.token)
                if (rollbackResult.isFailure) {
                    return Result.failure(
                        RestoreException(
                            "Restore failed and rollback also failed",
                            rollbackResult.exceptionOrNull()
                        )
                    )
                }
                cleanupSnapshot(snapshot!!)
                return Result.failure(error)
            }

            if (!applied.success) {
                val rollbackResult = applier.rollback(snapshot!!)
                preparedSnapshots.remove(snapshot!!.token)
                if (rollbackResult.isFailure) {
                    return Result.failure(
                        RestoreException(
                            "Restore reported failure and rollback also failed",
                            rollbackResult.exceptionOrNull()
                        )
                    )
                }
                cleanupSnapshot(snapshot!!)
                return Result.failure(RestoreException(applied.message))
            }

            preparedSnapshots.remove(snapshot!!.token)
            cleanupSnapshot(snapshot!!)
            Result.success(
                RestoreResult(
                    success = true,
                    restoredFileCount = applied.restoredFileCount,
                    restoredVaultItemCount = applied.restoredVaultItemCount,
                    backupInfo = decrypted.backupInfo,
                    snapshotToken = snapshot!!.token,
                    message = applied.message,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (cancelled: CancellationException) {
            if (snapshot != null) {
                val rollbackAttempt = withContext(NonCancellable) {
                    runCatching { applier.rollback(snapshot!!) }
                }
                preparedSnapshots.remove(snapshot!!.token)
                val rollbackResult = rollbackAttempt.getOrNull()
                if (rollbackAttempt.isFailure) {
                    cancelled.addSuppressed(rollbackAttempt.exceptionOrNull()!!)
                } else if (rollbackResult?.isFailure == true) {
                    cancelled.addSuppressed(rollbackResult.exceptionOrNull()!!)
                } else if (rollbackResult?.isSuccess == true) {
                    cleanupSnapshot(snapshot!!)
                }
            }
            throw cancelled
        } catch (error: Throwable) {
            if (snapshot != null) {
                val rollbackAttempt = runCatching { applier.rollback(snapshot!!) }
                preparedSnapshots.remove(snapshot!!.token)
                val rollbackResult = rollbackAttempt.getOrNull()
                if (rollbackAttempt.isFailure) {
                    return Result.failure(
                        RestoreException(
                            "Fail-closed restore aborted: rollback also failed",
                            rollbackAttempt.exceptionOrNull()
                        )
                    )
                }
                if (rollbackResult?.isFailure == true) {
                    return Result.failure(
                        RestoreException(
                            "Fail-closed restore aborted: rollback also failed",
                            rollbackResult.exceptionOrNull()
                        )
                    )
                }
                if (rollbackAttempt.isSuccess && rollbackResult?.isSuccess == true) {
                    cleanupSnapshot(snapshot!!)
                }
            }
            Result.failure(RestoreException("Fail-closed restore aborted", error))
        } finally {
            passphrase?.fill(0.toChar())
            stagingDir.deleteRecursively()
        }
    }

    override suspend fun dryRun(
        remoteBackupId: String,
        expectedChecksum: String?,
        passphrase: CharArray?
    ): Result<DryRunResult> = withContext(Dispatchers.IO) {
        dryRunOnIo(remoteBackupId, expectedChecksum, passphrase)
    }

    private suspend fun dryRunOnIo(
        remoteBackupId: String,
        expectedChecksum: String?,
        passphrase: CharArray?
    ): Result<DryRunResult> {
        if (remoteBackupId.isBlank()) {
            passphrase?.fill(0.toChar())
            return Result.failure(VerificationFailedException("Remote backup id is blank"))
        }
        val token = UUID.randomUUID().toString()
        val stagingDir = File(workingDir, "dry-run-$token")
        return try {
            requireWorkingDirectory(stagingDir)
            val downloaded = downloader.download(remoteBackupId).getOrElse { error ->
                if (error is CancellationException) throw error
                return Result.failure(error)
            }
            val verified = verifier.verify(downloaded, expectedChecksum).getOrElse { error ->
                if (error is CancellationException) throw error
                return Result.failure(error)
            }
            val decrypted = decryptor.decrypt(verified, stagingDir, passphrase).getOrElse { error ->
                if (error is CancellationException) throw error
                return Result.failure(error)
            }
            val fileCount = decrypted.stagingDir.walkTopDown().count { it.isFile }
            val vaultCount = decrypted.vaultDir.walkTopDown().count { it.isFile }
            Result.success(
                DryRunResult(
                    backupInfo = decrypted.backupInfo,
                    fileCount = fileCount,
                    vaultItemCount = vaultCount,
                    totalSizeBytes = decrypted.stagingDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                    timestamp = System.currentTimeMillis(),
                    message = "Restore validation and decryption completed without mutating live data"
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(RestoreException("Fail-closed restore dry-run aborted", error))
        } finally {
            passphrase?.fill(0.toChar())
            stagingDir.deleteRecursively()
        }
    }

    override suspend fun rollback(snapshotToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (snapshotToken.isBlank()) {
            return@withContext Result.failure(VerificationFailedException("Snapshot token is blank"))
        }
        val snapshot = preparedSnapshots[snapshotToken]
            ?: return@withContext Result.failure(VerificationFailedException("Snapshot token is not active in this process"))
        val result = applier.rollback(snapshot)
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (result.isSuccess) {
            preparedSnapshots.remove(snapshotToken)
            cleanupSnapshot(snapshot)
        }
        result
    }

    private fun cleanupSnapshot(snapshot: RestoreSnapshot) {
        runCatching { snapshot.databaseSnapshotPath.delete() }
        runCatching { snapshot.databaseWalSnapshotPath?.delete() }
        runCatching { snapshot.keyStateSnapshotPath?.deleteRecursively() }
        runCatching { snapshot.vaultSnapshotPath.deleteRecursively() }
    }

    private fun requireWorkingDirectory(stagingDir: File) {
        if (!workingDir.exists() && !workingDir.mkdirs()) {
            throw RestoreException("Unable to create restore working directory")
        }
        if (!workingDir.isDirectory) {
            throw RestoreException("Restore working path is not a directory")
        }
        if (!stagingDir.mkdirs()) {
            throw RestoreException("Unable to create isolated restore staging directory")
        }
    }
}
