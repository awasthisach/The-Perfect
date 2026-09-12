package com.vvf.smartmanager.core.data.backup

import android.util.Log
import java.io.File
import java.io.IOException

/** Read-only source used to stage backup data without mutating the live source. */
interface SnapshotSource {
    val sourceName: String

    /** Returns a staged file or directory, or null when the source cannot be read. */
    fun snapshot(stagingDir: File): File?

    /** Returns the size of the source data in bytes, best effort. */
    fun dataSizeBytes(): Long
}

/**
 * File snapshot of a SQLCipher/SQLite database including WAL/SHM sidecars.
 *
 * Prefer providing [beforeSnapshot] (e.g. WAL checkpoint) so the copy is consistent.
 * Path resolution tries [databaseFile] and sibling candidates under the same databases/ dir
 * so OEM / Room naming quirks do not yield a silent "Snapshot failed for: database".
 */
class ReadOnlyDatabaseSnapshotSource(
    private val databaseFile: File,
    private val beforeSnapshot: (() -> Unit)? = null,
    private val afterSnapshot: (() -> Unit)? = null
) : SnapshotSource {
    override val sourceName: String = "database"

    override fun snapshot(stagingDir: File): File? {
        val source = resolveExistingDatabaseFile()
        if (source == null) {
            Log.e(
                TAG,
                "DB snapshot missing. expected=${databaseFile.absolutePath} " +
                    "parentExists=${databaseFile.parentFile?.isDirectory} " +
                    "siblings=${databaseFile.parentFile?.list()?.joinToString().orEmpty()}"
            )
            // Fresh install may not have flushed a file yet — stage an empty marker so
            // cloud backup is not hard-blocked; restore treats missing DB payload carefully.
            return runCatching {
                val target = File(stagingDir, databaseFile.name.ifBlank { "vvf_smart_manager_enc.db" })
                target.parentFile?.mkdirs()
                if (!target.exists()) target.writeBytes(ByteArray(0))
                target
            }.getOrElse { err ->
                Log.e(TAG, "Failed to stage empty DB placeholder", err)
                null
            }
        }

        return try {
            beforeSnapshot?.invoke()
            try {
                val target = File(stagingDir, source.name)
                copyFileStreaming(source, target)
                copyIfPresent(File(source.path + "-wal"), File(target.path + "-wal"))
                copyIfPresent(File(source.path + "-shm"), File(target.path + "-shm"))
                if (!target.isFile) {
                    throw IOException("DB copy produced no file at ${target.absolutePath}")
                }
                target
            } finally {
                afterSnapshot?.invoke()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "DB snapshot copy failed from ${source.absolutePath}", error)
            // Propagate so ArchiveService surfaces the real cause, not a bare key name.
            throw IOException(
                "Database snapshot failed (${source.absolutePath}): ${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    override fun dataSizeBytes(): Long {
        val primary = resolveExistingDatabaseFile() ?: databaseFile
        return totalSize(primary, "-wal", "-shm")
    }

    /**
     * Room/SQLCipher usually uses Context.getDatabasePath(name). Some devices or prior
     * builds may leave the file under a slightly different name in the same folder.
     */
    private fun resolveExistingDatabaseFile(): File? {
        if (databaseFile.isFile && databaseFile.length() >= 0L) return databaseFile

        val parent = databaseFile.parentFile
        val candidates = mutableListOf<File>()
        if (parent != null) {
            candidates += File(parent, databaseFile.name)
            candidates += File(parent, "vvf_smart_manager_enc.db")
            candidates += File(parent, "vvf_smart_manager.db")
            parent.listFiles()
                ?.filter { it.isFile && it.name.startsWith("vvf_smart_manager") && !it.name.contains("-wal") && !it.name.contains("-shm") }
                ?.let { candidates.addAll(it) }
        }

        return candidates.firstOrNull { it.isFile }
    }

    private fun copyFileStreaming(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, bufferSize = DEFAULT_BUFFER)
                output.flush()
            }
        }
    }

    private fun copyIfPresent(source: File, target: File) {
        if (source.isFile) copyFileStreaming(source, target)
    }

    private fun totalSize(primary: File, vararg suffixes: String): Long {
        return (listOf(primary) + suffixes.map { File(primary.path + it) })
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    companion object {
        private const val TAG = "ReadOnlyDbSnapshot"
        private const val DEFAULT_BUFFER = 64 * 1024
    }
}

/**
 * Stages an injected vault directory. No absolute path or application singleton is used.
 */
class InjectedVaultSnapshotSource(
    private val vaultDirectory: File
) : SnapshotSource {
    override val sourceName: String = "vault"

    override fun snapshot(stagingDir: File): File? {
        if (!vaultDirectory.exists()) {
            vaultDirectory.mkdirs()
        }
        if (!vaultDirectory.isDirectory) return null
        return runCatching {
            val target = File(stagingDir, "vault")
            copyDirectory(vaultDirectory, target)
            target
        }.getOrNull()
    }

    override fun dataSizeBytes(): Long {
        if (!vaultDirectory.isDirectory) return 0L
        return vaultDirectory.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun copyDirectory(source: File, target: File) {
        require(source.canonicalFile != target.canonicalFile) { "Cannot copy a directory into itself" }
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source)
            val destination = if (relative.path.isEmpty()) target else File(target, relative.path)
            if (file.isDirectory) destination.mkdirs() else file.copyTo(destination, overwrite = true)
        }
    }
}
