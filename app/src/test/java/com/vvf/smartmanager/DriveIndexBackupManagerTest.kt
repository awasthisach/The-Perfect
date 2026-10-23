package com.vvf.smartmanager

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class DriveIndexBackupManagerTest {
    @Test fun importInvalidatesAllOldCheckpointsBeforeReplacingRows() = verifyImport(false)
    @Test fun failedCheckpointPersistenceLeavesExistingIndexIntact() = verifyImport(true)

    private fun verifyImport(failCommit: Boolean) = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val db = VVFDatabase.buildInMemoryDatabase(base)
        val prefs = base.getSharedPreferences("drive_search_index", Context.MODE_PRIVATE)
        val keys = listOf("full_list_next_page_token", "full_list_changes_cursor", "full_list_run_id", "changes_cursor")
        prefs.edit().clear().apply { keys.forEach { putString(it, "stale") }; putInt("full_list_indexed_count", 99) }.commit()
        val app = mockk<VVFApplication>(relaxed = true)
        every { app.database } returns db
        every { app.isDriveFullContentConsentEnabled() } returns false
        val badPrefs = mockk<SharedPreferences>()
        val badEditor = mockk<SharedPreferences.Editor>()
        every { badPrefs.getString("account_email", null) } returns null
        every { badPrefs.edit() } returns badEditor
        every { badEditor.remove(any()) } returns badEditor
        every { badEditor.commit() } returns false
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = app
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (failCommit && name == "drive_search_index") badPrefs else super.getSharedPreferences(name, mode)
        }
        val backup = File(base.cacheDir, "checkpoint-import.json")
        backup.writeText("""{"format":"drive-semantic-search-index","schemaVersion":1,"accountEmail":"","files":[]}""")
        try {
            db.fileDao().insertOrUpdate(FileMetadataEntity(path="gdrive://old",name="old.txt",parentPath="gdrive://root",
                sizeBytes=1,mimeType="text/plain",isDirectory=false,modifiedDate=1))
            val result = DriveIndexBackupManager(context).import(Uri.fromFile(backup))
            if (failCommit) {
                assertTrue(result.isFailure)
                io.mockk.verify(exactly = 1) { badEditor.commit() }
                assertNotNull(db.fileDao().getByPath("gdrive://old"))
            } else {
                result.getOrThrow()
                assertTrue(db.fileDao().getDriveIndexRows().isEmpty())
                (keys + "full_list_indexed_count").forEach { assertFalse(it, prefs.contains(it)) }
            }
        } finally { db.close(); backup.delete(); prefs.edit().clear().commit() }
    }
}
