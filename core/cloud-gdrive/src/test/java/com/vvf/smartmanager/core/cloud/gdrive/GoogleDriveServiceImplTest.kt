package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
    fun tokenAcquiredBeforeLogoutCannotBeRepublished() {
        val generation = service.tokenGeneration()
        service.setAccessToken(null)
        assertFalse(service.publishAccessToken("synthetic-stale-token", generation))
        assertTrue(service.publishAccessToken("synthetic-current-token", service.tokenGeneration()))
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
    fun listDriveFilesPage_withoutToken_fails() = runBlocking {
        val result = service.listDriveFilesPage(pageToken = null, pageSize = 1000)
        assertTrue(result.isFailure)
    }

    @Test
    fun getStorageQuota_withoutToken_fails() = runBlocking {
        val result = service.getStorageQuota()
        assertTrue(result.isFailure)
    }

    @Test
    fun getFileSha256_withoutToken_failsClosed() = runBlocking {
        val result = service.getFileSha256("123456789012345678901234567890123")
        assertTrue(result.isFailure)
    }

    @Test
    fun listDriveBackups_withoutToken_failsClosed() = runBlocking {
        val result = service.listDriveBackups()
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
    fun backupListingRequiresOwnedFolderAndOnlyVerifiableDigests() = runBlocking {
        val folder = "folder01234567890123456789"
        val valid = DriveFileDto(id="backup01234567890123456789", name="valid.vvfbak", appProperties=mapOf("vvfSha256" to "A".repeat(64)))
        val queries = mutableListOf<String>()
        val api = java.lang.reflect.Proxy.newProxyInstance(DriveApi::class.java.classLoader, arrayOf(DriveApi::class.java)) { _, method, args ->
            check(method.name == "listFiles")
            val query = args!![1] as String
            queries.add(query)
            if (query.contains("name = 'VVF_Backups'")) {
                assertTrue(query.contains("'me' in owners"))
                DriveFileListResponse(listOf(DriveFileDto(id=folder)))
            } else DriveFileListResponse(listOf(valid, valid.copy(appProperties=emptyMap()), valid.copy(appProperties=mapOf("vvfSha256" to "bad"))))
        } as DriveApi
        val authenticated = GoogleDriveServiceImpl(context, api).apply { setAccessToken("synthetic") }
        val backups = authenticated.listDriveBackups().getOrThrow()
        org.junit.Assert.assertEquals(1, backups.size)
        org.junit.Assert.assertEquals("a".repeat(64), backups.single().checksumSha256)
        org.junit.Assert.assertEquals(2, queries.size)
    }

    @Test
    fun backupUploadFolderResolutionRequiresAccountOwnership() = runBlocking {
        var querySeen = false
        val api = java.lang.reflect.Proxy.newProxyInstance(DriveApi::class.java.classLoader, arrayOf(DriveApi::class.java)) { _, method, args ->
            check(method.name == "listFiles")
            assertTrue((args!![1] as String).contains("'me' in owners"))
            querySeen = true
            throw IllegalStateException("Stop before any upload")
        } as DriveApi
        val source = java.io.File(context.cacheDir, "backup-test.vvfbak").apply { writeText("fixture") }
        try {
            val authenticated = GoogleDriveServiceImpl(context, api).apply { setAccessToken("synthetic") }
            assertTrue(authenticated.uploadFile(com.vvf.smartmanager.core.model.FileItem(source.path,source.name,source.length(),0,false), "VVF_Backups").isFailure)
            assertTrue(querySeen)
        } finally { source.delete() }
    }

}
