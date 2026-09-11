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
 * Prefer providing [beforeSnapshot] to checkpoint writers so the copy is not torn.
 * Avoid permanently closing Room unless [afterSnapshot] reopens it.
 */
class ReadOnlyDatabaseSnapshotSource(
    private val databaseFile: File,
    private val beforeSnapshot: (() -> Unit)? = null,
    private val afterSnapshot: (() -> Unit)? = null
) : SnapshotSource {
    override val sourceName: String = "database"

    override fun snapshot(stagingDir: File): File? {
        val source = resolveExistingDatabaseFile() ?: return null
        return runCatching {
            beforeSnapshot?.invoke()
            try {
                val target = File(stagingDir, source.name)
                source.copyTo(target, overwrite = true)
                copyIfPresent(File(source.path + "-wal"), File(target.path + "-wal"))
                copyIfPresent(File(source.path + "-shm"), File(target.path + "-shm"))
                target
            } finally {
                afterSnapshot?.invoke()
            }
        }.getOrNull()
    }

    /** Prefer the configured path; fall back to sibling common Room locations. */
    private fun resolveExistingDatabaseFile(): File? {
        if (databaseFile.isFile) return databaseFile
        val parent = databaseFile.parentFile ?: return null
        val candidates = listOf(
            databaseFile,
            File(parent, databaseFile.name),
            File(File(parent.parentFile, "databases"), databaseFile.name),
            File(File(parent, "databases"), databaseFile.name)
        )
        return candidates.firstOrNull { it.isFile }
    }

    override fun dataSizeBytes(): Long {
        val source = resolveExistingDatabaseFile() ?: return 0L
        return totalSize(source, "-wal", "-shm")
    }

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

    override fun dataSizeBytes(): Long {
        if (!vaultDirectory.isDirectory) return 0L
        return vaultDirectory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun copyDirectory(source: File, target: File) {
        if (!target.exists() && !target.mkdirs()) {
            error("Unable to create vault staging directory: ${target.absolutePath}")
        }
        source.listFiles()?.forEach { child ->
            val out = File(target, child.name)
            if (child.isDirectory) copyDirectory(child, out)
            else child.copyTo(out, overwrite = true)
        }
    }
}
