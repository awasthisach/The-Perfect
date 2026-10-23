package com.vvf.smartmanager.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** SQLCipher 4.19 retains the password by reference, including across lazy opens.
 * Own one buffer per helper lifetime; obtain a fresh buffer after close/failure.
 * The provider must return an independently owned, non-persisted plaintext buffer.
 */
internal class KeyedOpenHelperFactory(
    private val keyProvider: () -> ByteArray
) : SupportSQLiteOpenHelper.Factory {
    @Volatile private var paused = false
    private val helpers = mutableListOf<ManagedHelper>()

    /** Deny new DAO opens, close existing pools, and wait for outstanding native references. */
    @Synchronized
    fun pause() {
        check(!paused) { "Database maintenance already active" }
        paused = true
        val databases = helpers.mapNotNull { it.closeForMaintenance() }
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (databases.any { it.isOpen }) {
            check(System.nanoTime() < deadline) { "Database users did not quiesce; restore refused" }
            Thread.sleep(10)
        }
    }

    fun resume() { paused = false }

    @Synchronized
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        ManagedHelper(configuration).also { helpers.add(it) }

    private inner class ManagedHelper(private val configuration: SupportSQLiteOpenHelper.Configuration) : SupportSQLiteOpenHelper {
            private var currentDatabase: SupportSQLiteDatabase? = null
            private var key: ByteArray? = null
            private var delegate: SupportSQLiteOpenHelper? = null
            private var wal = false
            override val databaseName: String? get() = configuration.name

            private fun helper(): SupportSQLiteOpenHelper {
                check(!paused) { "Database maintenance in progress" }
                delegate?.let { return it }
                val owned = keyProvider()
                require(owned.isNotEmpty()) { "An encrypted database key is required" }
                try {
                    return SupportOpenHelperFactory(owned).create(configuration).also {
                        it.setWriteAheadLoggingEnabled(wal)
                        key = owned
                        delegate = it
                    }
                } catch (error: Throwable) {
                    owned.fill(0)
                    throw error
                }
            }

            @Synchronized
            override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
                wal = enabled
                delegate?.setWriteAheadLoggingEnabled(enabled)
            }

            private fun open(writable: Boolean): SupportSQLiteDatabase = try {
                (if (writable) helper().writableDatabase else helper().readableDatabase).also { currentDatabase = it }
            } catch (error: Throwable) {
                close()
                throw error
            }

            override val writableDatabase: SupportSQLiteDatabase
                @Synchronized get() = open(true)
            override val readableDatabase: SupportSQLiteDatabase
                @Synchronized get() = open(false)

            @Synchronized
            fun closeForMaintenance(): SupportSQLiteDatabase? {
                val database = currentDatabase
                close()
                return database
            }

            @Synchronized
            override fun close() {
                try {
                    delegate?.close()
                } finally {
                    delegate = null
                    currentDatabase = null
                    key?.fill(0)
                    key = null
                }
            }
        }
}
