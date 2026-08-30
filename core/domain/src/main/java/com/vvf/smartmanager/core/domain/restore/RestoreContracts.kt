package com.vvf.smartmanager.core.domain.restore

import com.vvf.smartmanager.core.model.CloudBackupInfo
import java.io.File

/** Contract for downloading a provider-assigned backup artifact. */
interface BackupDownloader {
    suspend fun download(remoteBackupId: String): Result<DownloadedArtifact>
}

/** Contract for verifying artifact integrity and structure before decryption. */
interface BackupVerifier {
    suspend fun verify(
        artifact: DownloadedArtifact,
        expectedChecksum: String?
    ): Result<VerifiedArtifact>
}

/** Contract for decrypting a verified artifact into an isolated staging directory. */
interface BackupDecryptor {
    suspend fun decrypt(
        verifiedArtifact: VerifiedArtifact,
        stagingDir: File
    ): Result<DecryptedBackup>
}

/** Contract for preparing, atomically applying, and rolling back a restore. */
interface RestoreApplier {
    suspend fun prepare(): Result<RestoreSnapshot>

    suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore>

    suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit>
}

data class DownloadedArtifact(
    val file: File,
    val backupInfo: CloudBackupInfo,
    val checksumSha256: String?
)

data class VerifiedArtifact(
    val file: File,
    val backupInfo: CloudBackupInfo,
    val checksumSha256: String
)

data class DecryptedBackup(
    val stagingDir: File,
    val databaseFile: File,
    val vaultDir: File,
    val backupInfo: CloudBackupInfo,
    val timestamp: Long
)

data class RestoreSnapshot(
    val token: String,
    val timestamp: Long,
    val databaseSnapshotPath: File,
    val vaultSnapshotPath: File
)

data class AppliedRestore(
    val success: Boolean,
    val restoredFileCount: Int,
    val restoredVaultItemCount: Int,
    val message: String
)

open class RestoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ChecksumMismatchException(expected: String, actual: String) :
    RestoreException("Checksum mismatch: expected $expected, got $actual")

class VerificationFailedException(reason: String) :
    RestoreException("Verification failed: $reason")
