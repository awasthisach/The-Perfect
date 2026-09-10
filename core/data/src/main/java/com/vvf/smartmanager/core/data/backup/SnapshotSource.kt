package com.vvf.smartmanager.core.data.backup

import java.io.File

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
 * Prefer providing [beforeSnapshot] to close Room briefly so the copy is not torn
 * by concurrent writers. [afterSnapshot] can reopen / resume work.
 */
class ReadOnlyDatabaseSnapshotSource(
    private val databaseFile: File,
    private val beforeSnapshot: (() -> Unit)? = null,
    private val afterSnapshot: (() -> Unit)? = null
) : SnapshotSource {
    override val sourceName: String = "database"

    override fun snapshot(stagingDir: File): File? {
        if (!databaseFile.isFile) return null
        return runCatching {
            beforeSnapshot?.invoke()
            try {
                val target = File(stagingDir, databaseFile.name)
                databaseFile.copyTo(target, overwrite = true)
                copyIfPresent(File(databaseFile.path + "-wal"), File(target.path + "-wal"))
                copyIfPresent(File(databaseFile.path + "-shm"), File(target.path + "-shm"))
                target
            } finally {
                afterSnapshot?.invoke()
            }
        }.getOrNull()
    }

    override fun dataSizeBytes(): Long = totalSize(databaseFile, "-wal", "-shm")

    private fun copyIfPresent(source: File, target: File) {
        if (source.isFile) source.copyTo(target, overwrite = true)
    }

    private fun totalSize(primary: File, vararg suffixes: String): Long {
        return (listOf(primary) + suffixes.map { File(primary.path + it) })
            .filter { it.isFile }
            .sumOf { it.length() }
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
        if (!vaultDirectory.isDirectory) return null
        return runCatching {
            val target = File(stagingDir, "vault")
            copyDirectory(vaultDirectory, target)
            target
        }.getOrNull()
    }

    override fun dataSizeBytes(): Long = vaultDirectory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }

    private fun copyDirectory(source: File, target: File) {
        require(source.canonicalFile != target.canonicalFile) { "Cannot copy a directory into itself" }
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source)
            val destination = if (relative.path.isEmpty()) target else File(target, relative.path)
            if (file.isDirectory) destination.mkdirs() else file.copyTo(destination, overwrite = true)
        }
    }
}
