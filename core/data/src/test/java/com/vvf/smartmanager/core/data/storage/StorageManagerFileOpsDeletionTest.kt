package com.vvf.smartmanager.core.data.storage

import android.content.Context
import com.vvf.smartmanager.core.database.dao.FileDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class StorageManagerFileOpsDeletionTest {

    @Test
    fun permanentDeleteFailurePreservesFileAndDatabaseMetadata() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val daoCalls = mutableListOf<String>()
        val ops = FailingDeleteStorageManager(context, recordingDao(daoCalls))
        val target = File(context.filesDir, "permanent-delete-failure.txt").apply {
            writeText("keep-me")
        }

        val result = ops.permanentDelete(listOf(target.absolutePath))

        assertTrue(result.isFailure)
        assertTrue(target.exists())
        assertFalse(daoCalls.contains("deleteByPath"))
    }

    @Test
    fun moveFallbackCopyFailurePreservesOriginalAndMetadata() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val daoCalls = mutableListOf<String>()
        val ops = FailingCopyStorageManager(context, recordingDao(daoCalls))
        val source = File(context.filesDir, "move-fallback-source.txt").apply { writeText("keep-me") }
        val destination = File(context.filesDir, "move-fallback-destination").apply { mkdirs() }

        val result = ops.moveFiles(listOf(source.absolutePath), destination.absolutePath)

        assertTrue(result.isFailure)
        assertTrue(source.exists())
        assertFalse(daoCalls.contains("deleteByPath"))
    }

    @Test
    fun trashFallbackCopyFailurePreservesOriginalAndMetadata() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val daoCalls = mutableListOf<String>()
        val ops = FailingCopyStorageManager(context, recordingDao(daoCalls))
        val source = File(context.filesDir, "trash-fallback-source.txt").apply { writeText("keep-me") }

        val result = ops.moveToRecycleBin(listOf(source.absolutePath))

        assertTrue(result.isFailure)
        assertTrue(source.exists())
        assertFalse(daoCalls.contains("deleteByPath"))
    }

    @Test
    fun copyFilesCopiesDirectoryContents() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val ops = FailingDeleteStorageManager(context, recordingDao(mutableListOf()))
        val source = File(context.filesDir, "copy-directory-source").apply {
            mkdirs()
            File(this, "nested.txt").writeText("verified-content")
        }
        val destination = File(context.filesDir, "copy-directory-destination").apply { mkdirs() }

        val result = ops.copyFiles(listOf(source.absolutePath), destination.absolutePath)

        assertTrue(result.isSuccess)
        assertEquals("verified-content", File(destination, "copy-directory-source/nested.txt").readText())
    }

    @Test
    fun copyDirectoryIntoItsOwnDescendantIsRejectedWithoutCreatingDestination() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val ops = FailingDeleteStorageManager(context, recordingDao(mutableListOf()))
        val source = File(context.filesDir, "copy-self-source").apply {
            mkdirs()
            File(this, "nested.txt").writeText("keep-me")
        }
        val destination = File(source, "nested-destination")

        val result = ops.copyFiles(listOf(source.absolutePath), destination.absolutePath)

        assertTrue(result.isFailure)
        assertTrue(source.exists())
        assertEquals("keep-me", File(source, "nested.txt").readText())
        assertFalse(destination.exists())
    }

    @Test
    fun moveDirectoryIntoItsOwnDescendantIsRejectedWithoutChangingSource() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val daoCalls = mutableListOf<String>()
        val ops = FailingDeleteStorageManager(context, recordingDao(daoCalls))
        val source = File(context.filesDir, "move-self-source").apply {
            mkdirs()
            File(this, "nested.txt").writeText("keep-me")
        }
        val destination = File(source, "nested-destination")

        val result = ops.moveFiles(listOf(source.absolutePath), destination.absolutePath)

        assertTrue(result.isFailure)
        assertTrue(source.exists())
        assertEquals("keep-me", File(source, "nested.txt").readText())
        assertFalse(destination.exists())
        assertFalse(daoCalls.contains("deleteByPath"))
    }

    @Test
    fun emptyRecycleBinFailureDoesNotClearTrashMetadata() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val daoCalls = mutableListOf<String>()
        val ops = FailingDeleteStorageManager(context, recordingDao(daoCalls))
        val target = File(ops.testTrashDirectory(), "trash-delete-failure.txt").apply {
            parentFile?.mkdirs()
            writeText("keep-me")
        }

        val result = ops.emptyRecycleBin()

        assertTrue(result.isFailure)
        assertTrue(target.exists())
        assertFalse(daoCalls.contains("emptyTrash"))
    }

    private fun recordingDao(calls: MutableList<String>): FileDao =
        Proxy.newProxyInstance(
            FileDao::class.java.classLoader,
            arrayOf(FileDao::class.java)
        ) { _, method, _ ->
            calls += method.name
            when (method.returnType) {
                java.lang.Long.TYPE -> 0L
                java.lang.Integer.TYPE -> 0
                java.lang.Boolean.TYPE -> false
                else -> null
            }
        } as FileDao

    private class FailingCopyStorageManager(
        context: Context,
        dao: FileDao
    ) : StorageManagerFileOps(context, dao) {
        override fun renamePhysical(source: File, destination: File): Boolean = false
        override fun copyPhysical(source: File, destination: File): Boolean = false
    }

    private class FailingDeleteStorageManager(
        context: Context,
        dao: FileDao
    ) : StorageManagerFileOps(context, dao) {
        override fun deletePhysical(file: File): Boolean = false
        fun testTrashDirectory(): File = trashDirectory
    }
}
