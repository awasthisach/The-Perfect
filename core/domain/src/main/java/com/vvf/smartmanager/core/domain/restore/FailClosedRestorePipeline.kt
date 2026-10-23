package com.vvf.smartmanager.core.domain.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    private val restoreMutex = Mutex()

    override suspend fun restore(remoteBackupId: String, expectedChecksum: String?): Result<RestoreResult> =
        restoreMutex.withLock { restoreLocked(remoteBackupId, expectedChecksum) }

    private val preparedSnapshots = ConcurrentHashMap<String, RestoreSnapshot>()

    private suspend fun restoreLocked(
        remoteBackupId: String,
        expectedChecksum: String?
    ): Result<RestoreResult> {
        if (remoteBackupId.isBlank()) {
            return Result.failure(VerificationFailedException("Remote backup id is blank"))
        }

        val token = UUID.randomUUID().toString()
        val stagingDir = File(workingDir, "restore-$token")
        var snapshot: RestoreSnapshot? = null
        var downloadedArtifact: DownloadedArtifact? = null
        return try {
            requireWorkingDirectory(stagingDir)

            val downloaded = downloader.download(remoteBackupId).getOrElse { return Result.failure(it) }
            downloadedArtifact = downloaded
            val verified = verifier.verify(downloaded, expectedChecksum).getOrElse { return Result.failure(it) }
            val decrypted = decryptor.decrypt(verified, stagingDir).getOrElse { return Result.failure(it) }

            snapshot = applier.prepare().getOrElse { return Result.failure(it) }
            preparedSnapshots[snapshot!!.token] = snapshot!!

            val applied = applier.apply(decrypted).getOrElse { error ->
                val rollbackResult = applier.rollback(snapshot!!)
                if (rollbackResult.isFailure) {
                    // Keep the snapshot reachable so a later recovery attempt can retry rollback.
                    return Result.failure(
                        RestoreException(
                            "Restore failed and rollback also failed",
                            rollbackResult.exceptionOrNull()
                        )
                    )
                }
                preparedSnapshots.remove(snapshot!!.token)
                deleteSnapshotFiles(snapshot!!)
                return Result.failure(error)
            }

            if (!applied.success) {
                val rollbackResult = applier.rollback(snapshot!!)
                if (rollbackResult.isFailure) {
                    // Keep the snapshot reachable so a later recovery attempt can retry rollback.
                    return Result.failure(
                        RestoreException(
                            "Restore reported failure and rollback also failed",
                            rollbackResult.exceptionOrNull()
                        )
                    )
                }
                preparedSnapshots.remove(snapshot!!.token)
                deleteSnapshotFiles(snapshot!!)
                return Result.failure(RestoreException(applied.message))
            }

            // Keep the snapshot available for the explicit rollback(token) API.
            Result.success(
                RestoreResult(
                    success = true,
                    restoredFileCount = applied.restoredFileCount,
                    restoredVaultItemCount = applied.restoredVaultItemCount,
                    backupInfo = verified.backupInfo,
                    snapshotToken = snapshot!!.token,
                    message = applied.message,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (error: Throwable) {
            if (snapshot != null) {
                val rollbackAttempt = withContext(NonCancellable) { runCatching { applier.rollback(snapshot!!) } }
                val rollbackResult = rollbackAttempt.getOrNull()
                if (rollbackAttempt.isFailure) {
                    // Retain the snapshot after rollback failure for a retry/recovery attempt.
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
                preparedSnapshots.remove(snapshot!!.token)
                deleteSnapshotFiles(snapshot!!)
            }
            if (error is CancellationException) throw error
            Result.failure(RestoreException("Fail-closed restore aborted", error))
        } finally {
            stagingDir.deleteRecursively()
            downloadedArtifact?.file?.delete()
        }
    }

    override suspend fun dryRun(
        remoteBackupId: String,
        expectedChecksum: String?
    ): Result<DryRunResult> {
        if (remoteBackupId.isBlank()) {
            return Result.failure(VerificationFailedException("Remote backup id is blank"))
        }
        val token = UUID.randomUUID().toString()
        val stagingDir = File(workingDir, "dry-run-$token")
        var downloadedArtifact: DownloadedArtifact? = null
        return try {
            requireWorkingDirectory(stagingDir)
            val downloaded = downloader.download(remoteBackupId).getOrElse { return Result.failure(it) }
            downloadedArtifact = downloaded
            val verified = verifier.verify(downloaded, expectedChecksum).getOrElse { return Result.failure(it) }
            val decrypted = decryptor.decrypt(verified, stagingDir).getOrElse { return Result.failure(it) }
            val fileCount = decrypted.stagingDir.walkTopDown().count { it.isFile }
            val vaultCount = decrypted.vaultDir.walkTopDown().count { it.isFile }
            Result.success(
                DryRunResult(
                    backupInfo = verified.backupInfo,
                    fileCount = fileCount,
                    vaultItemCount = vaultCount,
                    totalSizeBytes = decrypted.stagingDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                    timestamp = System.currentTimeMillis(),
                    message = "Restore validation and decryption completed without mutating live data"
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(RestoreException("Fail-closed restore dry-run aborted", error))
        } finally {
            stagingDir.deleteRecursively()
            downloadedArtifact?.file?.delete()
        }
    }

    override suspend fun rollback(snapshotToken: String): Result<Unit> =
        restoreMutex.withLock { rollbackLocked(snapshotToken) }

    private suspend fun rollbackLocked(snapshotToken: String): Result<Unit> {
        if (snapshotToken.isBlank()) {
            return Result.failure(VerificationFailedException("Snapshot token is blank"))
        }
        val snapshot = preparedSnapshots[snapshotToken]
            ?: return Result.failure(VerificationFailedException("Snapshot token is not active in this process"))
        return applier.rollback(snapshot).also {
            if (it.isSuccess) {
                preparedSnapshots.remove(snapshotToken)
                deleteSnapshotFiles(snapshot)
            }
        }
    }

    private fun deleteSnapshotFiles(snapshot: RestoreSnapshot) {
        runCatching { snapshot.databaseSnapshotPath.delete() }
        runCatching {
            if (snapshot.vaultSnapshotPath.isDirectory) snapshot.vaultSnapshotPath.deleteRecursively()
            else snapshot.vaultSnapshotPath.delete()
        }
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
