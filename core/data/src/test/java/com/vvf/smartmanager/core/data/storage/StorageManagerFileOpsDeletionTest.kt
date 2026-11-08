package com.vvf.smartmanager.core.data.storage

import android.content.Context
import com.vvf.smartmanager.core.database.dao.FileDao
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

    private class FailingDeleteStorageManager(
        context: Context,
        dao: FileDao
    ) : StorageManagerFileOps(context, dao) {
        override fun deletePhysical(file: File): Boolean = false
        fun testTrashDirectory(): File = trashDirectory
    }
}
