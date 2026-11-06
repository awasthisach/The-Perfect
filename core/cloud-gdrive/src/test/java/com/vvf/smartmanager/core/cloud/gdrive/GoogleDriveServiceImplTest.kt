package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertTrue
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
        service = GoogleDriveServiceImpl(context)
        service.disconnect()
    }

    @Test
    fun authenticate_withoutToken_fails() = runBlocking {
        val result = service.authenticate()
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("token", ignoreCase = true) || msg.contains("OAuth", ignoreCase = true))
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
    fun disconnect_clearsConnectedState() {
        service.setAccessToken("dummy-not-used-without-network")
        service.disconnect()
        assertTrue(!service.getAccountInfo().isConnected)
    }

    @Test
    fun driveNetwork_createApi_succeeds() {
        val api = DriveNetwork.createApi(debugLogging = false)
        assertTrue(api != null)
    }
    @Test
    fun listDriveFiles_followsEveryPageAndRestartsFromFirstPageOnRejectedToken() = runBlocking {
        val fake = FakeDriveApi(
            responses = mutableListOf(
                DriveFileListResponse(files = listOf(file("discarded")), nextPageToken = "expired"),
                DriveFileListResponse(files = listOf(file("first")), nextPageToken = "fresh"),
                DriveFileListResponse(files = listOf(file("second")))
            )
        )
        val pagedService = GoogleDriveServiceImpl(context, fake)
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
        val pagedService = GoogleDriveServiceImpl(context, fake)
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
        val pagedService = GoogleDriveServiceImpl(context, fake)
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
        val pagedService = GoogleDriveServiceImpl(context, fake)
        pagedService.setAccessToken("test-token")

        val result = pagedService.listDriveFiles("root")

        assertTrue(result.isFailure)
        assertEquals(listOf(null, "expired", null, "expired-again"), fake.requestedPageTokens)
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
        private val pageErrors: Map<String, HttpException> = emptyMap()
    ) : DriveApi {
        val requestedPageTokens = mutableListOf<String?>()

        override suspend fun listFiles(
            bearer: String,
            query: String?,
            spaces: String,
            fields: String,
            pageSize: Int,
            pageToken: String?
        ): DriveFileListResponse {
            requestedPageTokens += pageToken
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

        override suspend fun about(bearer: String, fields: String) =
            throw UnsupportedOperationException()
    }

}
