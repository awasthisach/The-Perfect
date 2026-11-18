package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GoogleDrivePaginationTest {

    private class FakeDriveApi(
        private val pages: List<DriveFileListResponse>,
        private val failureOnCall: Int? = null,
        private val failure: Exception? = null
    ) : DriveApi {
        val requestedPageTokens = mutableListOf<String?>()
        var calls: Int = 0
            private set

        override suspend fun listFiles(
            bearer: String,
            query: String?,
            spaces: String,
            fields: String,
            pageSize: Int,
            pageToken: String?
        ): DriveFileListResponse {
            calls++
            requestedPageTokens += pageToken
            if (calls == failureOnCall) throw failure ?: IOException("simulated Drive page failure")
            return pages[calls - 1]
        }

        override suspend fun downloadFile(bearer: String, fileId: String, alt: String): ResponseBody =
            throw UnsupportedOperationException("not used in pagination tests")

        override suspend fun uploadFile(
            bearer: String,
            metadata: RequestBody,
            file: MultipartBody.Part,
            fields: String
        ): DriveFileDto = throw UnsupportedOperationException("not used in pagination tests")

        override suspend fun createFolder(
            bearer: String,
            metadata: RequestBody,
            fields: String
        ): DriveFileDto = throw UnsupportedOperationException("not used in pagination tests")

        override suspend fun about(bearer: String, fields: String): DriveAboutResponse =
            throw UnsupportedOperationException("not used in pagination tests")
    }

    private fun service(api: DriveApi): GoogleDriveServiceImpl {
        val context: Context = RuntimeEnvironment.getApplication()
        return GoogleDriveServiceImpl(context, api, DriveAccessTokenProvider { "test-access-token" })
    }

    @Test
    fun listingFollowsEveryPageAndReturnsAllFiles() = runBlocking {
        val api = FakeDriveApi(
            pages = listOf(
                DriveFileListResponse(
                    files = listOf(DriveFileDto(id = "file-1", name = "one.pdf")),
                    nextPageToken = "page-2"
                ),
                DriveFileListResponse(
                    files = listOf(DriveFileDto(id = "file-2", name = "two.pdf"))
                )
            )
        )

        val result = service(api).listDriveFiles("root")

        assertTrue(result.isSuccess)
        assertEquals(listOf(null, "page-2"), api.requestedPageTokens)
        assertEquals(listOf("gdrive://file-1", "gdrive://file-2"), result.getOrThrow().map { it.path })
    }

    @Test
    fun repeatedPageTokenFailsInsteadOfLoopingOrReturningPartialResults() = runBlocking {
        val api = FakeDriveApi(
            pages = listOf(
                DriveFileListResponse(nextPageToken = "repeat"),
                DriveFileListResponse(nextPageToken = "repeat")
            )
        )

        val result = service(api).listDriveFiles("root")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(2, api.calls)
    }

    @Test
    fun failureOnLaterPageDoesNotReturnPartialSuccess() = runBlocking {
        val api = FakeDriveApi(
            pages = listOf(DriveFileListResponse(nextPageToken = "page-2")),
            failureOnCall = 2,
            failure = IOException("page two unavailable")
        )

        val result = service(api).listDriveFiles("root")

        assertTrue(result.isFailure)
        assertEquals("page two unavailable", result.exceptionOrNull()?.message)
        assertEquals(2, api.calls)
    }

    @Test
    fun cancellationDuringPaginationPropagates() = runBlocking {
        val api = FakeDriveApi(
            pages = emptyList(),
            failureOnCall = 1,
            failure = CancellationException("cancelled while listing Drive")
        )

        try {
            service(api).listDriveFiles("root")
            throw AssertionError("Expected CancellationException to propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("cancelled while listing Drive", cancelled.message)
        }
    }
}
