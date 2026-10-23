package com.vvf.smartmanager.core.data.storage
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.model.FileSortOption
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StorageReadPathTest {
    @Test fun emptyAndMissingDirectoriesNeverCreateSampleFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "read-path").apply { mkdirs() }
        try {
            val manager = StorageManager(context, mock())
            assertTrue(manager.listDirectory(root.path, FileSortOption.entries.first(), false).isEmpty())
            val missing = File(root, "missing")
            assertTrue(manager.listDirectory(missing.path, FileSortOption.entries.first(), false).isEmpty())
            assertTrue(root.listFiles()!!.isEmpty())
            assertFalse(missing.exists())
        } finally { root.deleteRecursively() }
    }
}
