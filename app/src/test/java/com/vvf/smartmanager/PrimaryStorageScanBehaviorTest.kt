package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.data.storage.StorageManagerImpl
import com.vvf.smartmanager.core.database.VVFDatabase
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrimaryStorageScanBehaviorTest {
    private lateinit var context: Context
    private lateinit var database: VVFDatabase
    private lateinit var root: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VVFDatabase.buildInMemoryDatabase(context)
        root = File(context.cacheDir, "primary-scan-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun unreadableDirectoryMarksScanIncompleteButDoesNotStopLaterSiblings() {
        val unreadable = File(root, "a-unreadable").apply { mkdirs() }
        val readable = File(root, "b-readable").apply { mkdirs() }
        val visibleFile = File(readable, "visible.txt").apply { writeText("visible") }

        val scan = manager(unreadableNames = setOf(unreadable.name)).collectPrimaryStorageScan()

        assertFalse(scan.isComplete)
        assertTrue(scan.items.any { it.path == visibleFile.absolutePath })
    }

    @Test
    fun danglingSymlinkDoesNotMakeOtherwiseCompleteScanIncomplete() {
        val visibleFile = File(root, "visible.txt").apply { writeText("visible") }
        val dangling = File(root, "dangling-link")
        try {
            Files.createSymbolicLink(dangling.toPath(), File(root, "missing-target").toPath())
        } catch (error: Exception) {
            assumeNoException(error)
        }

        val scan = manager().collectPrimaryStorageScan()

        assertTrue(scan.isComplete)
        assertEquals(listOf(visibleFile.absolutePath), scan.items.map { it.path })
    }

    @Test
    fun outOfRootDirectorySymlinkIsExcludedWithoutBlockingPruning() {
        val outside = File(context.cacheDir, "outside-scan-${UUID.randomUUID()}").apply { mkdirs() }
        val outsideFile = File(outside, "secret.txt").apply { writeText("outside") }
        val visibleFile = File(root, "visible.txt").apply { writeText("visible") }
        try {
            Files.createSymbolicLink(File(root, "outside-link").toPath(), outside.toPath())
        } catch (error: Exception) {
            outside.deleteRecursively()
            assumeNoException(error)
        }

        val scan = manager().collectPrimaryStorageScan()

        assertTrue(scan.isComplete)
        assertTrue(scan.items.any { it.path == visibleFile.absolutePath })
        assertFalse(scan.items.any { it.path == outsideFile.absolutePath })
        outside.deleteRecursively()
    }

    @Test
    fun inRootDirectoryAliasMakesPathInventoryIncomplete() {
        val target = File(root, "a-target").apply { mkdirs() }
        val targetFile = File(target, "document.txt").apply { writeText("content") }
        try {
            Files.createSymbolicLink(File(root, "z-alias").toPath(), target.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
        }

        val scan = manager().collectPrimaryStorageScan()

        assertFalse(scan.isComplete)
        assertTrue(scan.items.any { it.path == targetFile.absolutePath })
    }

    @Test
    fun reachingItemLimitMarksScanIncomplete() {
        File(root, "a.txt").writeText("a")
        File(root, "b.txt").writeText("b")

        val scan = manager().collectPrimaryStorageScan(maxItems = 1)

        assertEquals(1, scan.items.size)
        assertFalse(scan.isComplete)
    }

    private fun manager(unreadableNames: Set<String> = emptySet()) =
        object : StorageManagerImpl(context, database.fileDao()) {
            override fun getPrimaryStoragePath(): String = root.absolutePath

            override fun canReadPath(file: File): Boolean =
                file.name !in unreadableNames && super.canReadPath(file)

            override fun listDirectoryChildren(directory: File): Array<File>? =
                super.listDirectoryChildren(directory)?.sortedBy { it.name }?.toTypedArray()
        }
}
