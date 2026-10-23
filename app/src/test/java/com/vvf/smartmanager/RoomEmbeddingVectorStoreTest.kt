package com.vvf.smartmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class RoomEmbeddingVectorStoreTest {
    @Test fun contentModelAndAccountBoundariesNeverReuseIncompatibleVectors() = runBlocking {
        val db = VVFDatabase.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        try {
            val path = "gdrive://synthetic"
            val row = FileMetadataEntity(path = path, name = "a.txt", parentPath = "gdrive://root", sizeBytes = 1,
                mimeType = "text/plain", isDirectory = false, modifiedDate = 1, contentText = "original")
            val id = db.fileDao().insertOrUpdate(row)
            val store = RoomEmbeddingVectorStore(db.semanticEmbeddingDao())
            val vector = FloatArray(768) { 0.125f }
            store.put(path, "content-1", "model-4", vector)
            db.fileDao().insertOrUpdate(row.copy(isFavorite = true))
            assertEquals(id, db.fileDao().getByPath(path)!!.id)
            assertArrayEquals(vector, store.get(path, "content-1", "model-4"), 0f)
            assertNull(store.get(path, "content-2", "model-4"))
            assertNull(store.get(path, "content-1", "model-5"))
            store.put(path, "content-2", "model-5", vector)
            assertNull(store.get(path, "content-1", "model-4"))
            assertNotNull(store.get(path, "content-2", "model-5"))
            db.fileDao().deleteAllDriveIndexRows()
            db.fileDao().insertOrUpdate(row)
            assertNull(store.get(path, "content-2", "model-5"))
        } finally { db.close() }
    }
}
