package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.backup.ArchiveMetadata
import com.vvf.smartmanager.core.domain.restore.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

/** Same-device restore with a durable undo journal. Recover before opening the database.
 * Callers must quiesce database/vault writers for prepare/apply/rollback.
 * Publication uses same-filesystem rename only; never copy over a live database.
 */
class LocalRestoreApplier(
    private val liveDatabaseFile: File,
    private val liveVaultDir: File,
    private val snapshotRoot: File,
    private val vaultAuthImporter: ((Map<String, String>) -> Boolean)? = null,
    private val beforeApply: (() -> Unit)? = null,
    private val vaultAuthExporter: (() -> Map<String, String>)? = null,
    private val vaultAuthRestorer: ((Map<String, String>) -> Boolean)? = null,
    private val validateDatabase: ((File) -> Unit)? = null,
    private val afterApply: (() -> Unit)? = null
) : RestoreApplier {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val journal get() = File(snapshotRoot, "pending.properties")
    private var active: RestoreSnapshot? = null
    private var maintenanceStarted = false

    /** Startup-only, before new restore requests. Failed recovery retains its journal and snapshots. */
    fun recoverInterrupted(): Result<Unit> = runCatching {
        if (journal.isFile) {
            val p = Properties().apply { journal.inputStream().use { load(it) } }
            val token = p.getProperty("token")
            require(token.matches(Regex("[a-f0-9-]{36}"))) { "Invalid recovery token" }
            restoreSnapshot(RestoreSnapshot(token, 0, File(snapshotRoot, "$token-db"),
                File(snapshotRoot, "$token-vault"), p.getProperty("databaseExisted").toBooleanStrict(),
                p.getProperty("vaultExisted").toBooleanStrict()))
        }
        // Completed restores have only process-local rollback tokens. After restart,
        // discard their unreachable undo copies, including WAL/SHM and wrapped auth.
        check(!journal.exists())
        snapshotRoot.listFiles().orEmpty().filter {
            it.name.matches(Regex("[a-f0-9-]{36}-(db(?:-wal|-shm)?|vault|auth\\.json)")) || it.name == "pending.tmp"
        }.forEach(::remove)
    }

    override suspend fun prepare(): Result<RestoreSnapshot> = runCatching {
        check(!journal.exists()) { "An interrupted restore must be recovered first" }
        beforeApply?.invoke() // close/checkpoint BEFORE taking the undo snapshot
        maintenanceStarted = true
        check(snapshotRoot.isDirectory || snapshotRoot.mkdirs())
        val token = UUID.randomUUID().toString()
        val snapshot = RestoreSnapshot(token, System.currentTimeMillis(), File(snapshotRoot, "$token-db"),
            File(snapshotRoot, "$token-vault"), liveDatabaseFile.isFile, liveVaultDir.isDirectory)
        if (snapshot.databaseExisted) copyDatabase(liveDatabaseFile, snapshot.databaseSnapshotPath)
        if (snapshot.vaultExisted) copyTree(liveVaultDir, snapshot.vaultSnapshotPath)
        vaultAuthExporter?.let { exporter ->
            durableWrite(authFile(snapshot), json.encodeToString(mapSerializer, exporter()).toByteArray())
        }
        val p = Properties().apply {
            setProperty("token", token); setProperty("databaseExisted", snapshot.databaseExisted.toString())
            setProperty("vaultExisted", snapshot.vaultExisted.toString())
        }
        val tmp = File(snapshotRoot, "pending.tmp")
        FileOutputStream(tmp).use { p.store(it, null); it.fd.sync() }
        check(tmp.renameTo(journal)) { "Cannot persist restore journal" }
        active = snapshot
        snapshot
    }.onFailure {
        if (maintenanceStarted && !journal.exists()) {
            maintenanceStarted = false
            afterApply?.invoke()
        }
    }

    override suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore> = runCatching {
        val manifest = json.decodeFromString(ArchiveMetadata.serializer(), File(decryptedBackup.stagingDir, "metadata.json").readText())
        require(manifest.includesDatabase || manifest.includesVault) { "No restorable components" }
        if (manifest.includesDatabase) {
            require(decryptedBackup.databaseFile.isFile && decryptedBackup.databaseFile.length() > 0) { "Database payload missing" }
        }
        if (manifest.includesVault) require(decryptedBackup.vaultDir.isDirectory) { "Vault payload missing" }
        require(decryptedBackup.stagingDir.walkTopDown().count { it.isFile && it.name != "metadata.json" } == manifest.fileCount) { "Incomplete or unexpected backup payload" }
        if (manifest.includesVault) require(decryptedBackup.vaultDir.walkTopDown().count { it.isFile } == manifest.vaultItemCount) { "Incomplete vault" }
        val auth = if (manifest.includesVault && manifest.includesVaultAuth) {
            require(vaultAuthImporter != null && vaultAuthExporter != null && vaultAuthRestorer != null) { "Reversible vault authentication import required" }
            json.decodeFromString(mapSerializer, File(decryptedBackup.stagingDir, "vault_auth.json").readText())
        } else null
        if (manifest.includesDatabase) validateDatabase?.invoke(decryptedBackup.databaseFile)
        active ?: prepare().getOrThrow()
        // Snapshot and journal are durable before the first preference or live-file mutation.
        if (auth != null) check(vaultAuthImporter!!.invoke(auth)) { "Vault authentication import failed" }
        if (manifest.includesDatabase) publishDatabase(decryptedBackup.databaseFile, liveDatabaseFile)
        if (manifest.includesVault) publishTree(decryptedBackup.vaultDir, liveVaultDir)
        check(journal.delete()) { "Cannot commit restore journal" }
        active = null
        maintenanceStarted = false
        afterApply?.invoke()
        AppliedRestore(true, manifest.fileCount, manifest.vaultItemCount, "Restore applied")
    }

    override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> = runCatching {
        if (active == null) beforeApply?.invoke()
        restoreSnapshot(snapshot)
    }

    private fun restoreSnapshot(snapshot: RestoreSnapshot) {
        // Check every recovery source before touching live state.
        if (snapshot.databaseExisted) require(snapshot.databaseSnapshotPath.isFile) { "Database undo snapshot missing" }
        if (snapshot.vaultExisted) require(snapshot.vaultSnapshotPath.isDirectory) { "Vault undo snapshot missing" }
        val auth = authFile(snapshot)
        if (vaultAuthRestorer != null || vaultAuthExporter != null) {
            require(auth.isFile) { "Vault auth undo snapshot missing" }
        }
        val metadata = if (auth.isFile) json.decodeFromString(mapSerializer, auth.readText()) else null
        if (metadata != null) require(vaultAuthRestorer != null) { "Vault auth recovery unavailable" }
        if (snapshot.databaseExisted) publishDatabase(snapshot.databaseSnapshotPath, liveDatabaseFile)
        else databaseFiles(liveDatabaseFile).forEach { remove(it) }
        if (snapshot.vaultExisted) publishTree(snapshot.vaultSnapshotPath, liveVaultDir) else remove(liveVaultDir)
        if (metadata != null) check(vaultAuthRestorer!!.invoke(metadata)) { "Vault auth rollback failed" }
        if (journal.exists()) check(journal.delete()) { "Cannot finalize rollback" }
        active = null
        maintenanceStarted = false
        afterApply?.invoke()
        databaseFiles(snapshot.databaseSnapshotPath).forEach(::remove)
        remove(snapshot.vaultSnapshotPath)
        remove(authFile(snapshot))
    }

    private fun authFile(snapshot: RestoreSnapshot) = File(snapshotRoot, "${snapshot.token}-auth.json")
    private fun databaseFiles(file: File) = listOf(file, File(file.path + "-wal"), File(file.path + "-shm"))
    private fun copyDatabase(source: File, target: File) {
        databaseFiles(source).zip(databaseFiles(target)).forEach { (from, to) -> if (from.isFile) copyFile(from, to) }
    }
    private fun publishDatabase(source: File, target: File) {
        require(source.isFile) { "Database publication source disappeared" }
        val staged = File(target.parentFile, "${target.name}.restore-new")
        databaseFiles(staged).forEach(::remove)
        copyDatabase(source, staged)
        // No open connection may observe the swap. Undo files remain available on failure.
        databaseFiles(staged).zip(databaseFiles(target)).forEach { (from, to) ->
            if (from.exists()) check(from.renameTo(to)) { "Atomic database rename failed" } else remove(to)
        }
    }
    private fun publishTree(source: File, target: File) {
        val staged = File(target.parentFile, "${target.name}.restore-new")
        val displaced = File(target.parentFile, "${target.name}.restore-old")
        remove(staged); copyTree(source, staged); remove(displaced)
        if (target.exists()) check(target.renameTo(displaced)) { "Cannot move old vault" }
        check(staged.renameTo(target)) { "Atomic vault rename failed" }
        remove(displaced)
    }
    private fun copyTree(source: File, target: File) {
        check(target.isDirectory || target.mkdirs())
        source.walkTopDown().forEach { from ->
            require(from.canonicalFile == File(from.parentFile!!.canonicalFile, from.name)) { "Symlinks are not restore payloads" }
            val to = File(target, from.relativeTo(source).path)
            if (from.isDirectory) check(to.isDirectory || to.mkdirs()) else copyFile(from, to)
        }
    }
    private fun copyFile(from: File, to: File) {
        check(to.parentFile!!.isDirectory || to.parentFile!!.mkdirs())
        from.inputStream().use { input -> FileOutputStream(to).use { output -> input.copyTo(output); output.fd.sync() } }
        check(from.length() == to.length()) { "Incomplete copy" }
    }
    private fun durableWrite(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    private fun remove(file: File) {
        // A cleanup entry must never cause traversal through a link outside snapshots.
        if (file.canonicalFile != File(file.parentFile!!.canonicalFile, file.name)) {
            check(file.delete()) { "Cannot remove restore symlink" }
            return
        }
        if (file.exists()) check(if (file.isDirectory) file.deleteRecursively() else file.delete()) { "Cannot remove restore path" }
    }
}
