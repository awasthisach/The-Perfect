package com.vvf.smartmanager.core.database

import android.database.Cursor
import android.database.CursorWrapper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Own SQLCipher's retained key buffer until close, including lazy opens and failures.
 * Room can retain database/statement handles across maintenance. The stable facades below
 * reopen with a fresh owned key and never reuse a statement from the displaced database.
 */
internal class KeyedOpenHelperFactory(private val keyProvider: () -> ByteArray) : SupportSQLiteOpenHelper.Factory {
    @Volatile private var paused = false
    private val helpers = mutableListOf<ManagedHelper>()

    @Synchronized
    fun pause() {
        check(!paused) { "Database maintenance already active" }
        paused = true
        try {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (helpers.any { it.busy() }) {
                check(System.nanoTime() < deadline) { "Database users did not quiesce; restore refused" }
                Thread.sleep(10)
            }
            helpers.forEach { it.close() }
        } catch (error: Throwable) {
            resume()
            throw error
        }
    }

    @Synchronized
    fun resume() {
        paused = false
        helpers.forEach { it.wakeWaiters() }
    }

    @Synchronized
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        ManagedHelper(configuration).also { helpers.add(it) }

    private inner class ManagedHelper(private val configuration: SupportSQLiteOpenHelper.Configuration) : SupportSQLiteOpenHelper {
        private val monitor = java.lang.Object()
        private var raw: SupportSQLiteDatabase? = null
        private var key: ByteArray? = null
        private var delegate: SupportSQLiteOpenHelper? = null
        private var wal = false
        private var calls = 0
        private var cursors = 0
        private var transactions = 0
        private val depth = object : ThreadLocal<Int>() { override fun initialValue() = 0 }
        override val databaseName: String? get() = configuration.name

        fun busy() = synchronized(monitor) { calls != 0 || cursors != 0 || transactions != 0 }
        fun wakeWaiters() = synchronized(monitor) { monitor.notifyAll() }

        private fun database(): SupportSQLiteDatabase {
            raw?.let { return it }
            try {
                if (delegate == null) {
                    val owned = keyProvider()
                    key = owned
                    require(owned.isNotEmpty()) { "An encrypted database key is required" }
                    delegate = SupportOpenHelperFactory(owned).create(configuration).also {
                        it.setWriteAheadLoggingEnabled(wal)
                    }
                }
                return delegate!!.writableDatabase.also { raw = it }
            } catch (error: Throwable) {
                close()
                throw error
            }
        }

        /** Keep the database open through an entire call, transaction, or cursor lifetime.
         * Existing transactions can finish while paused; new users wait for resume.
         */
        private fun <T> useDatabase(block: (SupportSQLiteDatabase) -> T): T {
            val db = synchronized(monitor) {
                while (paused && depth.get() == 0) monitor.wait()
                database().also { calls++ }
            }
            try { return block(db) }
            finally { synchronized(monitor) { calls--; monitor.notifyAll() } }
        }

        private val facade: SupportSQLiteDatabase = proxy(SupportSQLiteDatabase::class.java) { method, args ->
            when (method.name) {
                "close" -> { close(); null }
                "isOpen" -> synchronized(monitor) { raw?.isOpen == true }
                "compileStatement" -> statement(args[0] as String)
                else -> useDatabase { db ->
                    val ending = method.name == "endTransaction"
                    try {
                        val result = invoke(method, db, args)
                        if (method.name.startsWith("beginTransaction")) synchronized(monitor) {
                            transactions++
                            depth.set(depth.get() + 1)
                        }
                        if (result is Cursor) {
                            synchronized(monitor) { cursors++ }
                            object : CursorWrapper(result) {
                                private var released = false
                                override fun close() {
                                    try { super.close() } finally {
                                        synchronized(monitor) {
                                            if (!released) { released = true; cursors--; monitor.notifyAll() }
                                        }
                                    }
                                }
                            }
                        } else result
                    } finally {
                        if (ending && depth.get() > 0) synchronized(monitor) {
                            depth.set(depth.get() - 1)
                            transactions--
                        }
                    }
                }
            }
        }

        /** Room caches statements. Bindings survive a helper reopen; native statements do not. */
        private fun statement(sql: String): SupportSQLiteStatement {
            val bindings = linkedMapOf<Int, Pair<Method, Array<out Any?>>>()
            var closed = false
            return proxy(SupportSQLiteStatement::class.java) { method, args ->
                synchronized(bindings) {
                    if (method.name == "close") { bindings.clear(); closed = true; return@synchronized null }
                    check(!closed) { "Statement is closed" }
                    when {
                        method.name.startsWith("bind") -> { bindings[args[0] as Int] = method to args; null }
                        method.name == "clearBindings" -> { bindings.clear(); null }
                        else -> useDatabase { db ->
                            db.compileStatement(sql).use { compiled ->
                                bindings.values.forEach { (bind, values) -> invoke(bind, compiled, values) }
                                invoke(method, compiled, args)
                            }
                        }
                    }
                }
            }
        }

        override val writableDatabase: SupportSQLiteDatabase
            get() = if (paused) facade else useDatabase { facade }
        override val readableDatabase: SupportSQLiteDatabase get() = writableDatabase
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) = synchronized(monitor) {
            wal = enabled
            delegate?.setWriteAheadLoggingEnabled(enabled)
            Unit
        }
        override fun close() {
            synchronized(monitor) {
                try { delegate?.close() } finally {
                    raw = null
                    delegate = null
                    key?.fill(0)
                    key = null
                }
            }
        }
    }

    private fun invoke(method: Method, target: Any, args: Array<out Any?>): Any? = try {
        method.invoke(target, *args)
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    private fun <T> proxy(type: Class<T>, call: (Method, Array<out Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
            when (method.name) {
                "toString" -> "Managed${type.simpleName}"
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.firstOrNull()
                else -> call(method, args ?: emptyArray())
            }
        }
    )
}
