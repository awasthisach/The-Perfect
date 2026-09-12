package com.vvf.smartmanager.core.data.backup

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
 * Path resolution tries [databaseFile] and sibling candidates under the same databases/ dir.
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
            // Fresh install / empty DB: stage empty placeholder so cloud backup is not hard-blocked.
            return runCatching {
                val target = File(stagingDir, databaseFile.name.ifBlank { "vvf_smart_manager_enc.db" })
                target.parentFile?.mkdirs()
                if (!target.exists()) {
                    target.writeBytes(ByteArray(0))
                }
                target
            }.getOrNull()
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

    private fun resolveExistingDatabaseFile(): File? {
        if (databaseFile.isFile) return databaseFile

        val parent = databaseFile.parentFile ?: return null
        val candidates = mutableListOf(
            File(parent, databaseFile.name),
            File(parent, "vvf_smart_manager_enc.db"),
            File(parent, "vvf_smart_manager.db")
        )
        parent.listFiles()
            ?.filter {
                it.isFile &&
                    it.name.startsWith("vvf_smart_manager") &&
                    !it.name.contains("-wal") &&
                    !it.name.contains("-shm")
            }
            ?.let { candidates.addAll(it) }

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
        private const val DEFAULT_BUFFER = 64 * 1024
    }
}

/** Stages an injected vault directory. */
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
