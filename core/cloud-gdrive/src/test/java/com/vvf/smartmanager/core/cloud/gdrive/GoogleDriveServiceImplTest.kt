package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ensures production Drive service never pretends to be connected without a token.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveServiceImplTest {

    private lateinit var context: Context
    private lateinit var service: GoogleDriveServiceImpl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        service = GoogleDriveServiceImpl(
            context,
            accessTokenProvider = DriveAccessTokenProvider {
                throw DriveAuthorizationRequiredException("Google Drive authorization required")
            }
        )
    }

    @Test
    fun authenticate_withoutToken_fails() = runBlocking {
        val result = service.authenticate()
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("token", ignoreCase = true) || msg.contains("OAuth", ignoreCase = true) || msg.contains("authorization", ignoreCase = true))
    }

    @Test
    fun listDriveFiles_withoutToken_fails() = runBlocking {
        val result = service.listDriveFiles("root")
        assertTrue(result.isFailure)
    }

    @Test
    fun getStorageQuota_withoutToken_fails() = runBlocking {
        val result = service.getStorageQuota()
        assertTrue(result.isFailure)
    }

    @Test
    fun disconnect_revokesGrantAndClearsLocalState() = runBlocking {
        var revoked = false
        val provider = object : DriveAccessTokenProvider {
            override suspend fun getAccessToken(): String = "test-token"
            override suspend fun revokeAccess() { revoked = true }
        }
        val connectedService = GoogleDriveServiceImpl(context, accessTokenProvider = provider)
        connectedService.setAccessToken("test-token")

        val result = connectedService.disconnect()

        assertTrue(result.isSuccess)
        assertTrue(revoked)
        assertFalse(connectedService.getAccountInfo().isConnected)
    }

    @Test
    fun disconnectClearsLocalStateEvenWhenRemoteRevocationFails() = runBlocking {
        val provider = object : DriveAccessTokenProvider {
            override suspend fun getAccessToken(): String = "test-token"
            override suspend fun revokeAccess() { throw IllegalStateException("revoke failed") }
        }
        val connectedService = GoogleDriveServiceImpl(context, accessTokenProvider = provider)
        connectedService.setAccessToken("test-token")

        val result = connectedService.disconnect()

        assertTrue(result.isFailure)
        assertFalse(connectedService.getAccountInfo().isConnected)
    }

    @Test
    fun disconnectOfflineOnlyClearsLocalSessionWithoutClaimingRemoteRevocation() = runBlocking {
        var revoked = false
        val provider = object : DriveAccessTokenProvider {
            override suspend fun getAccessToken(): String = "test-token"
            override suspend fun revokeAccess() { revoked = true }
        }
        val offlineService = GoogleDriveServiceImpl(
            context = context,
            accessTokenProvider = provider,
            networkAllowed = { false }
        )
        offlineService.setAccessToken("test-token")

        val result = offlineService.disconnect()

        assertTrue(result.isFailure)
        assertFalse(revoked)
        assertFalse(offlineService.getAccountInfo().isConnected)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not revoked"))
    }

    @Test
    fun googleDriveAuthorizationIsBlockedBeforeNetworkUseInOfflineOnlyMode() {
        val auth = GoogleDriveAuth(
            context = context,
            serverClientId = "unused-test-client-id",
            networkAllowed = { false }
        )

        try {
            auth.authorizeDriveAccess()
            throw AssertionError("Expected offline-only authorization to be blocked")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("Strict Offline-First"))
        }
    }

    @Test
    fun authenticatePropagatesCoroutineCancellation() = runBlocking {
        val api = FakeDriveApi(
            responses = mutableListOf(),
            aboutFailure = CancellationException("Drive request cancelled")
        )
        val cancellableService = GoogleDriveServiceImpl(
            context,
            api,
            DriveAccessTokenProvider { "test-token" }
        )
        try {
            cancellableService.authenticate()
            throw AssertionError("Expected CancellationException to propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("Drive request cancelled", cancelled.message)
        }
    }

    @Test
    fun driveNetwork_createApi_succeeds() {
        val api = DriveNetwork.createApi(debugLogging = false)
        assertTrue(api != null)
    }
    @Test
    fun listCloudBackups_countsEntriesWithoutValidChecksumAndExcludesThemFromRestoreList() = runBlocking {
        val validChecksum = "a".repeat(64)
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(
                    files = listOf(
                        DriveFileDto(id = "legacy-missing", name = "old-missing.vvfbak"),
                        DriveFileDto(
                            id = "legacy-invalid",
                            name = "old-invalid.vvfbak",
                            appProperties = mapOf("vvf_sha256" to "not-a-checksum")
                        ),
                        DriveFileDto(
                            id = "valid",
                            name = "valid.vvfbak",
                            appProperties = mapOf("vvf_sha256" to validChecksum)
                        )
                    )
                )
            )
        )
        val service = GoogleDriveServiceImpl(
            context,
            fake,
            DriveAccessTokenProvider { "test-token" }
        )

        val result = service.listCloudBackups("a".repeat(25))

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().legacyBackupCount)
        assertEquals(listOf("valid"), result.getOrThrow().backups.map { it.backupId })
        assertEquals(validChecksum, result.getOrThrow().backups.single().checksumSha256)
    }

    @Test
    fun listCloudBackups_followsEveryPageAndCountsLegacyEntriesAcrossPages() = runBlocking {
        val checksum = "b".repeat(64)
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(
                    files = listOf(
                        DriveFileDto(id = "valid-page-one", name = "one.vvfbak",
                            appProperties = mapOf("vvf_sha256" to checksum)),
                        DriveFileDto(id = "legacy-page-one", name = "old-one.vvfbak")
                    ),
                    nextPageToken = "page-two"
                ),
                DriveFileListResponse(
                    files = listOf(
                        DriveFileDto(id = "valid-page-two", name = "two.vvfbak",
                            appProperties = mapOf("vvf_sha256" to checksum)),
                        DriveFileDto(id = "legacy-page-two", name = "old-two.vvfbak",
                            appProperties = mapOf("vvf_sha256" to "invalid"))
                    )
                )
            )
        )
        val pagedService = GoogleDriveServiceImpl(
            context,
            fake,
            DriveAccessTokenProvider { "test-token" }
        )

        val result = pagedService.listCloudBackups("a".repeat(25))

        assertTrue(result.isSuccess)
        assertEquals(listOf("valid-page-one", "valid-page-two"),
            result.getOrThrow().backups.map { it.backupId })
        assertEquals(2, result.getOrThrow().legacyBackupCount)
        assertEquals(listOf(null, "page-two"), fake.requestedPageTokens)
    }

    @Test
    fun listCloudBackups_returnsAllEntriesAndLegacyCountAcrossMoreThanOneHundredFiles() = runBlocking {
        val checksum = "c".repeat(64)
        val legacyIndexes = setOf(10, 110, 150)
        fun backupFile(index: Int): DriveFileDto {
            val properties = when (index) {
                110, 150 -> mapOf("vvf_sha256" to "invalid-checksum")
                in legacyIndexes -> emptyMap()
                else -> mapOf("vvf_sha256" to checksum)
            }
            return DriveFileDto(
                id = "backup-$index",
                name = "backup-$index.vvfbak",
                appProperties = properties
            )
        }
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(
                    files = (0 until 100).map(::backupFile),
                    nextPageToken = "page-two"
                ),
                DriveFileListResponse(
                    files = (100 until 200).map(::backupFile),
                    nextPageToken = "page-three"
                ),
                DriveFileListResponse(
                    files = (200 until 205).map(::backupFile)
                )
            )
        )
        val pagedService = GoogleDriveServiceImpl(
            context,
            fake,
            DriveAccessTokenProvider { "test-token" }
        )

        val result = pagedService.listCloudBackups("a".repeat(25))

        assertTrue(result.isSuccess)
        val listing = result.getOrThrow()
        assertEquals(202, listing.backups.size)
        assertEquals(3, listing.legacyBackupCount)
        assertEquals("backup-0", listing.backups.first().backupId)
        assertEquals("backup-204", listing.backups.last().backupId)
        assertFalse(listing.backups.any { it.backupId in legacyIndexes.map { index -> "backup-$index" } })
        assertEquals(listOf(null, "page-two", "page-three"), fake.requestedPageTokens)
        assertEquals(listOf(100, 100, 100), fake.requestedPageSizes)
    }

    @Test
    fun listDriveFiles_followsEveryPageAndRestartsFromFirstPageOnRejectedToken() = runBlocking {
        val rejectedPageToken = httpError(
            400,
            """{"error":{"errors":[{"reason":"invalidPageToken"}]}}"""
        )
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(files = listOf(file("discarded")), nextPageToken = "expired"),
                DriveFileListResponse(files = listOf(file("first")), nextPageToken = "fresh"),
                DriveFileListResponse(files = listOf(file("second")))
            ),
            pageErrors = mapOf("expired" to rejectedPageToken)
        )
        val pagedService = GoogleDriveServiceImpl(context, fake, DriveAccessTokenProvider { "test-token" })
        pagedService.setAccessToken("test-token")

        val result = pagedService.listDriveFiles("root")

        assertTrue(result.isSuccess)
        assertEquals(listOf("first", "second"), result.getOrNull()?.map { it.name })
        assertEquals(listOf(null, "expired", null, "fresh"), fake.requestedPageTokens)
    }

    @Test
    fun listDriveFiles_repeatedPageTokenFailsInsteadOfReturningPartialData() = runBlocking {
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(files = listOf(file("first")), nextPageToken = "same"),
                DriveFileListResponse(files = listOf(file("second")), nextPageToken = "same")
            )
        )
        val pagedService = GoogleDriveServiceImpl(context, fake, DriveAccessTokenProvider { "test-token" })
        pagedService.setAccessToken("test-token")

        val result = pagedService.listDriveFiles("root")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("repeated page token"))
    }

    @Test
    fun listDriveFiles_incompleteSearchFailsClosed() = runBlocking {
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(files = listOf(file("partial")), incompleteSearch = true)
            )
        )
        val pagedService = GoogleDriveServiceImpl(context, fake, DriveAccessTokenProvider { "test-token" })
        pagedService.setAccessToken("test-token")

        val result = pagedService.listDriveFiles("root")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("incompleteSearch"))
    }

    @Test
    fun listDriveFiles_rejectedPageTokenRestartIsBounded() = runBlocking {
        val rejected = httpError(400, """{"error":{"errors":[{"reason":"invalidPageToken"}]}}""")
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(files = listOf(file("first")), nextPageToken = "expired"),
                DriveFileListResponse(files = listOf(file("again")), nextPageToken = "expired-again")
            ),
            pageErrors = mapOf("expired" to rejected, "expired-again" to rejected)
        )
        val pagedService = GoogleDriveServiceImpl(context, fake, DriveAccessTokenProvider { "test-token" })
        pagedService.setAccessToken("test-token")

        val result = pagedService.listDriveFiles("root")

        assertTrue(result.isFailure)
        assertEquals(listOf(null, "expired", null, "expired-again"), fake.requestedPageTokens)
    }

    @Test
    fun uploadMetadata_isValidJsonAndPersistsChecksumAsAppProperty() {
        val checksum = "a".repeat(64)
        val metadata = JSONObject(
            buildDriveUploadMetadataJson("backup \"final\".vvfbak", "folder-id", checksum)
        )

        assertEquals("backup \"final\".vvfbak", metadata.getString("name"))
        assertEquals("folder-id", metadata.getJSONArray("parents").getString(0))
        assertEquals(checksum, metadata.getJSONObject("appProperties").getString("vvf_sha256"))
    }

    @Test
    fun uploadMetadata_preservesNewlinesUnicodeAndJsonControlCharacters() {
        val fileName = "backup\nहिंदी-資料-\u0001\\\".vvfbak"
        val parentId = "folder-\n\u0002-id"
        val checksum = "d".repeat(64)

        val metadata = JSONObject(
            buildDriveUploadMetadataJson(fileName, parentId, checksum)
        )

        assertEquals(fileName, metadata.getString("name"))
        assertEquals(parentId, metadata.getJSONArray("parents").getString(0))
        assertEquals(checksum, metadata.getJSONObject("appProperties").getString("vvf_sha256"))
    }

    @Test
    fun ordinaryUploadMetadata_isValidJsonWithoutBackupChecksumProperty() {
        val metadata = JSONObject(buildDriveUploadMetadataJson("report \\ archive.pdf", "root", null))

        assertEquals("report \\ archive.pdf", metadata.getString("name"))
        assertEquals("root", metadata.getJSONArray("parents").getString(0))
        assertFalse(metadata.has("appProperties"))
    }

    @Test
    fun folderMetadata_isValidJsonAndEscapesFolderName() {
        val metadata = JSONObject(buildDriveFolderMetadataJson("VVF \"Backups\""))

        assertEquals("VVF \"Backups\"", metadata.getString("name"))
        assertEquals("application/vnd.google-apps.folder", metadata.getString("mimeType"))
        assertEquals("root", metadata.getJSONArray("parents").getString(0))
    }

    private fun file(name: String) = DriveFileDto(
        id = name,
        name = name,
        mimeType = "text/plain",
        size = "1"
    )

    private fun httpError(code: Int, body: String) = HttpException(
        Response.error<DriveFileListResponse>(
            code,
            body.toResponseBody("application/json".toMediaType())
        )
    )

    private class FakeDriveApi(
        private val responses: MutableList<DriveFileListResponse>,
        private val pageErrors: Map<String, HttpException> = emptyMap(),
        private val aboutFailure: Exception? = null
    ) : DriveApi {
        val requestedPageTokens = mutableListOf<String?>()
        val requestedPageSizes = mutableListOf<Int>()

        override suspend fun listFiles(
            bearer: String,
            query: String?,
            spaces: String,
            fields: String,
            pageSize: Int,
            pageToken: String?
        ): DriveFileListResponse {
            requestedPageTokens += pageToken
            requestedPageSizes += pageSize
            pageToken?.let { pageErrors[it]?.let { error -> throw error } }
            return responses.removeAt(0)
        }

        override suspend fun downloadFile(bearer: String, fileId: String, alt: String) =
            throw UnsupportedOperationException()

        override suspend fun uploadFile(
            bearer: String,
            metadata: okhttp3.RequestBody,
            file: okhttp3.MultipartBody.Part,
            fields: String
        ) = throw UnsupportedOperationException()

        override suspend fun createFolder(
            bearer: String,
            metadata: okhttp3.RequestBody,
            fields: String
        ) = throw UnsupportedOperationException()

        override suspend fun about(bearer: String, fields: String): DriveAboutResponse {
            aboutFailure?.let { throw it }
            throw UnsupportedOperationException()
        }
    }

}
