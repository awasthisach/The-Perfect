package com.vvf.smartmanager.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseLifecycleTest {
    @Test fun refusedMaintenanceAllowsDatabaseToOpenAgain() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "maintenance-failure.db"
        context.deleteDatabase(name)
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val db = VVFDatabase.buildEncryptedDatabase(context, name) { secret.copyOf() }
        try {
            val sql = db.openHelper.writableDatabase
            sql.beginTransaction()
            try {
                try { db.pauseForMaintenance(); fail("Active transaction allowed maintenance") }
                catch (_: IllegalStateException) { }
            } finally { sql.endTransaction() }
            db.openHelper.writableDatabase.query("SELECT 1").use { assertTrue(it.moveToFirst()) }
        } finally { db.close(); secret.fill(0); context.deleteDatabase(name) }
    }

    @Test fun stagedBackupRequiresValidEncryptedRoomDatabase() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = java.io.File(context.cacheDir, "native-backup-source.db")
        val stage = java.io.File(context.cacheDir, "native-backup-stage.db")
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val db = VVFDatabase.buildEncryptedDatabase(context, source.absolutePath) { secret.copyOf() }
        try {
            db.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use { assertTrue(it.moveToFirst()) }
        } finally { db.close() }
        try {
            source.copyTo(stage, overwrite = true)
            VVFDatabase.validateEncryptedBackup(context, stage) { secret.copyOf() }
            try {
                VVFDatabase.validateEncryptedBackup(context, stage) { ByteArray(32) { 1 } }
                fail("Wrong backup key accepted")
            } catch (_: Exception) { }
            java.io.RandomAccessFile(stage, "rw").use { it.seek(0); it.write(ByteArray(128)) }
            try {
                VVFDatabase.validateEncryptedBackup(context, stage) { secret.copyOf() }
                fail("Corrupted backup accepted")
            } catch (_: Exception) { }
        } finally {
            secret.fill(0)
            listOf(source, stage).forEach { file ->
                listOf(file, java.io.File(file.path + "-wal"), java.io.File(file.path + "-shm")).forEach { it.delete() }
            }
        }
    }

    @Test fun lazyOpenReopenWrongKeyAndFailureRecovery() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(VVFDatabase.DATABASE_NAME)
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val issued = mutableListOf<ByteArray>()
        val db = VVFDatabase.buildEncryptedDatabase(context) { secret.copyOf().also(issued::add) }
        try {
            assertTrue(issued.isEmpty())
            db.openHelper.writableDatabase.execSQL("CREATE TABLE lifecycle_probe(value TEXT NOT NULL)")
            db.openHelper.writableDatabase.execSQL("INSERT INTO lifecycle_probe VALUES ('retained')")
            assertTrue(issued.last().any { it != 0.toByte() })
            db.pauseForMaintenance()
            try { db.openHelper.writableDatabase; fail("maintenance allowed a new open") } catch (_: IllegalStateException) { }
            db.resumeAfterMaintenance()
            db.openHelper.close()
            assertTrue(issued.last().all { it == 0.toByte() })
            db.openHelper.writableDatabase.query("SELECT value FROM lifecycle_probe").use {
                assertTrue(it.moveToFirst()); assertEquals("retained", it.getString(0))
            }
        } finally { db.close() }
        for (bad in listOf(ByteArray(32), ByteArray(32) { 1 })) {
            var issuedKey: ByteArray? = null
            val wrong = VVFDatabase.buildEncryptedDatabase(context) { bad.copyOf().also { issuedKey = it } }
            try {
                try { wrong.openHelper.writableDatabase; fail("wrong key accepted") } catch (_: Exception) { }
                assertTrue(issuedKey!!.all { it == 0.toByte() })
            } finally { wrong.close() }
        }
        val recovered = VVFDatabase.buildEncryptedDatabase(context) { secret.copyOf() }
        try {
            recovered.openHelper.writableDatabase.query("SELECT value FROM lifecycle_probe").use {
                assertTrue(it.moveToFirst()); assertEquals("retained", it.getString(0))
            }
        } finally {
            recovered.close(); secret.fill(0); context.deleteDatabase(VVFDatabase.DATABASE_NAME)
        }
    }
}
