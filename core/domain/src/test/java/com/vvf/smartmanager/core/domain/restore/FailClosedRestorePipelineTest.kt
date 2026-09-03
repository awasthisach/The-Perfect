package com.vvf.smartmanager.core.domain.restore

import com.vvf.smartmanager.core.model.CloudBackupInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FailClosedRestorePipelineTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val sampleInfo = CloudBackupInfo(
        backupId = "remote-1",
        timestamp = 1L,
        backupName = "backup.vvfbak",
        backupSizeBytes = 10L,
        checksumSha256 = "abc"
    )

    private fun pipeline(
        downloadResult: Result<DownloadedArtifact> = Result.success(
            DownloadedArtifact(File(temp.root, "dl.bin").also { it.writeText("x") }, sampleInfo, "abc")
        ),
        verifyResult: Result<VerifiedArtifact> = Result.success(
            VerifiedArtifact(File(temp.root, "dl.bin"), sampleInfo, "abc")
        ),
        decryptResult: Result<DecryptedBackup> = Result.success(
            DecryptedBackup(
                stagingDir = temp.newFolder("stage"),
                databaseFile = temp.newFile("db"),
                vaultDir = temp.newFolder("vault"),
                backupInfo = sampleInfo,
                timestamp = 1L
            )
        ),
        prepareResult: Result<RestoreSnapshot> = Result.success(
            RestoreSnapshot("snap-1", 1L, temp.newFile("db-snap"), temp.newFolder("vault-snap"))
        ),
        applyResult: Result<AppliedRestore> = Result.success(
            AppliedRestore(true, 1, 0, "ok")
        ),
        rollbackOk: Boolean = true
    ): FailClosedRestorePipeline {
        return FailClosedRestorePipeline(
            workingDir = temp.newFolder("work"),
            downloader = object : BackupDownloader {
                override suspend fun download(remoteBackupId: String): Result<DownloadedArtifact> = downloadResult
            },
            verifier = object : BackupVerifier {
                override suspend fun verify(
                    artifact: DownloadedArtifact,
                    expectedChecksum: String?
                ): Result<VerifiedArtifact> = verifyResult
            },
            decryptor = object : BackupDecryptor {
                override suspend fun decrypt(
                    verifiedArtifact: VerifiedArtifact,
                    stagingDir: File
                ): Result<DecryptedBackup> = decryptResult
            },
            applier = object : RestoreApplier {
                override suspend fun prepare(): Result<RestoreSnapshot> = prepareResult
                override suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore> = applyResult
                override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> =
                    if (rollbackOk) Result.success(Unit) else Result.failure(RestoreException("rollback failed"))
            }
        )
    }

    @Test
    fun blankRemoteIdFailsClosed() = runBlocking {
        val result = pipeline().restore("  ", null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VerificationFailedException)
    }

    @Test
    fun successfulRestoreReturnsAppliedResult() = runBlocking {
        val result = pipeline().restore("remote-1", "abc")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().restoredFileCount)
    }

    @Test
    fun verifyFailureAbortsWithoutApply() = runBlocking {
        var applied = false
        val result = FailClosedRestorePipeline(
            workingDir = temp.newFolder("work2"),
            downloader = object : BackupDownloader {
                override suspend fun download(remoteBackupId: String): Result<DownloadedArtifact> = Result.success(
                    DownloadedArtifact(temp.newFile("d"), sampleInfo, "abc")
                )
            },
            verifier = object : BackupVerifier {
                override suspend fun verify(
                    artifact: DownloadedArtifact,
                    expectedChecksum: String?
                ): Result<VerifiedArtifact> =
                    Result.failure(ChecksumMismatchException("abc", "zzz"))
            },
            decryptor = object : BackupDecryptor {
                override suspend fun decrypt(
                    verifiedArtifact: VerifiedArtifact,
                    stagingDir: File
                ): Result<DecryptedBackup> =
                    error("should not decrypt")
            },
            applier = object : RestoreApplier {
                override suspend fun prepare(): Result<RestoreSnapshot> = error("should not prepare")
                override suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore> {
                    applied = true
                    return Result.success(AppliedRestore(true, 1, 0, "ok"))
                }
                override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> = Result.success(Unit)
            }
        ).restore("remote-1", "abc")
        assertTrue(result.isFailure)
        assertFalse(applied)
    }

    @Test
    fun applyFailureTriggersRollback() = runBlocking {
        var rolledBack = false
        val result = FailClosedRestorePipeline(
            workingDir = temp.newFolder("work3"),
            downloader = object : BackupDownloader {
                override suspend fun download(remoteBackupId: String): Result<DownloadedArtifact> = Result.success(
                    DownloadedArtifact(temp.newFile("d2"), sampleInfo, "abc")
                )
            },
            verifier = object : BackupVerifier {
                override suspend fun verify(
                    artifact: DownloadedArtifact,
                    expectedChecksum: String?
                ): Result<VerifiedArtifact> =
                    Result.success(VerifiedArtifact(artifact.file, sampleInfo, "abc"))
            },
            decryptor = object : BackupDecryptor {
                override suspend fun decrypt(
                    verifiedArtifact: VerifiedArtifact,
                    stagingDir: File
                ): Result<DecryptedBackup> =
                    Result.success(
                        DecryptedBackup(stagingDir, temp.newFile("db2"), temp.newFolder("v2"), sampleInfo, 1L)
                    )
            },
            applier = object : RestoreApplier {
                override suspend fun prepare(): Result<RestoreSnapshot> = Result.success(
                    RestoreSnapshot("t1", 1L, temp.newFile("sdb"), temp.newFolder("sv"))
                )
                override suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore> =
                    Result.failure(RestoreException("apply boom"))
                override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> {
                    rolledBack = true
                    return Result.success(Unit)
                }
            }
        ).restore("remote-1", "abc")
        assertTrue(result.isFailure)
        assertTrue(rolledBack)
    }

    @Test
    fun dryRunDoesNotLeaveStagingArtifacts() = runBlocking {
        val work = temp.newFolder("work4")
        val p = FailClosedRestorePipeline(
            workingDir = work,
            downloader = object : BackupDownloader {
                override suspend fun download(remoteBackupId: String): Result<DownloadedArtifact> = Result.success(
                    DownloadedArtifact(temp.newFile("d3"), sampleInfo, "abc")
                )
            },
            verifier = object : BackupVerifier {
                override suspend fun verify(
                    artifact: DownloadedArtifact,
                    expectedChecksum: String?
                ): Result<VerifiedArtifact> =
                    Result.success(VerifiedArtifact(artifact.file, sampleInfo, "abc"))
            },
            decryptor = object : BackupDecryptor {
                override suspend fun decrypt(
                    verifiedArtifact: VerifiedArtifact,
                    stagingDir: File
                ): Result<DecryptedBackup> =
                    Result.success(
                        DecryptedBackup(
                            stagingDir,
                            File(stagingDir, "db"),
                            File(stagingDir, "vault").also { it.mkdirs() },
                            sampleInfo,
                            1L
                        )
                    )
            },
            applier = object : RestoreApplier {
                override suspend fun prepare(): Result<RestoreSnapshot> = error("dry-run must not prepare")
                override suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore> =
                    error("dry-run must not apply")
                override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> = Result.success(Unit)
            }
        )
        val result = p.dryRun("remote-1", "abc")
        assertTrue(result.isSuccess)
        val leftover = work.listFiles()?.filter { it.name.startsWith("dry-run-") } ?: emptyList()
        assertTrue(leftover.isEmpty())
    }
}
