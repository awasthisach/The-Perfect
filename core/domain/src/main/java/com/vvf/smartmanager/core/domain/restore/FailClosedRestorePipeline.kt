package com.vvf.smartmanager.core.domain.restore

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

    private val preparedSnapshots = ConcurrentHashMap<String, RestoreSnapshot>()

    override suspend fun restore(
        remoteBackupId: String,
        expectedChecksum: String?
    ): Result<RestoreResult> {
        if (remoteBackupId.isBlank()) {
            return Result.failure(VerificationFailedException("Remote backup id is blank"))
        }

        val token = UUID.randomUUID().toString()
        val stagingDir = File(workingDir, "restore-$token")
        var snapshot: RestoreSnapshot? = null
        return try {
            requireWorkingDirectory(stagingDir)

            val downloaded = downloader.download(remoteBackupId).getOrElse { return Result.failure(it) }
            val verified = verifier.verify(downloaded, expectedChecksum).getOrElse { return Result.failure(it) }
            val decrypted = decryptor.decrypt(verified, stagingDir).getOrElse { return Result.failure(it) }

            snapshot = applier.prepare().getOrElse { return Result.failure(it) }
            preparedSnapshots[snapshot!!.token] = snapshot!!

            val applied = applier.apply(decrypted).getOrElse { error ->
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
                return Result.failure(RestoreException(applied.message))
            }

            preparedSnapshots.remove(snapshot!!.token)
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
            var finalError: Throwable = error
            if (snapshot != null) {
                val rollbackResult = runCatching { applier.rollback(snapshot!!) }
                val rollbackFailure = rollbackResult.getOrNull()?.exceptionOrNull()
                if (rollbackFailure != null) {
                    finalError = RestoreException(
                        "Fail-closed restore aborted and rollback also failed",
                        rollbackFailure
                    )
                } else if (rollbackResult.isFailure) {
                    finalError = RestoreException(
                        "Fail-closed restore aborted and rollback also failed",
                        rollbackResult.exceptionOrNull()
                    )
                }
                preparedSnapshots.remove(snapshot!!.token)
            }
            Result.failure(RestoreException("Fail-closed restore aborted", finalError))
        } finally {
            stagingDir.deleteRecursively()
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
        return try {
            requireWorkingDirectory(stagingDir)
            val downloaded = downloader.download(remoteBackupId).getOrElse { return Result.failure(it) }
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
            Result.failure(RestoreException("Fail-closed restore dry-run aborted", error))
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    override suspend fun rollback(snapshotToken: String): Result<Unit> {
        if (snapshotToken.isBlank()) {
            return Result.failure(VerificationFailedException("Snapshot token is blank"))
        }
        val snapshot = preparedSnapshots[snapshotToken]
            ?: return Result.failure(VerificationFailedException("Snapshot token is not active in this process"))
        return applier.rollback(snapshot).also {
            if (it.isSuccess) preparedSnapshots.remove(snapshotToken)
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
