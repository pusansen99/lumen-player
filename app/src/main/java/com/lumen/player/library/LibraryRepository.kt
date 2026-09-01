package com.lumen.player.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.lumen.player.library.data.LibraryDao
import com.lumen.player.library.data.LibraryFolder
import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.data.LumenDatabase
import com.lumen.player.library.scan.FolderScanner
import com.lumen.player.library.scan.ScanOutcome
import com.lumen.player.library.scan.diffVideos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "LibraryRepository"

/** Owns SAF folders and runs [FolderScanner]. Mirrors [HistoryRepository.get]. */
class LibraryRepository private constructor(
    private val dao: LibraryDao,
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutexes = HashMap<String, Mutex>()
    private fun mutexFor(treeUri: String) = synchronized(mutexes) { mutexes.getOrPut(treeUri) { Mutex() } }

    private val _scanning = MutableStateFlow<Set<String>>(emptySet())
    val scanning: StateFlow<Set<String>> = _scanning.asStateFlow()

    private val _foldersWithError = MutableStateFlow<Set<String>>(emptySet())
    val foldersWithError: StateFlow<Set<String>> = _foldersWithError.asStateFlow()

    val folders: Flow<List<LibraryFolder>> = dao.observeFolders()
    fun folderRows(treeUri: String): Flow<List<LibraryVideoRow>> = dao.observeFolderRows(treeUri)
    fun observeFolder(treeUri: String): Flow<LibraryFolder?> = dao.observeFolder(treeUri)

    sealed interface AddFolderResult {
        data class Ok(val treeUri: String) : AddFolderResult
        data object PermissionDenied : AddFolderResult
    }

    suspend fun addFolder(treeUri: Uri): AddFolderResult {
        val ok = runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (!ok) return AddFolderResult.PermissionDenied

        val key = treeUri.toString()
        val name = runCatching { DocumentFile.fromTreeUri(appContext, treeUri)?.name }.getOrNull()
            ?: treeUri.lastPathSegment?.substringAfterLast('/') ?: key
        dao.upsertFolder(LibraryFolder(key, name, System.currentTimeMillis(), 0L, 0))
        scope.launch { rescan(key) }
        return AddFolderResult.Ok(key)
    }

    suspend fun removeFolder(treeUri: String) {
        dao.deleteFolder(treeUri)   // FK cascade drops library_video rows
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                treeUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        _foldersWithError.update { it - treeUri }
    }

    suspend fun rescan(treeUri: String): ScanOutcome = mutexFor(treeUri).withLock {
        val folder = dao.folder(treeUri) ?: return ScanOutcome.Ok(emptyList())
        _scanning.update { it + treeUri }
        try {
            when (val out = FolderScanner.scan(appContext.contentResolver, folder)) {
                is ScanOutcome.PermissionLost -> {
                    _foldersWithError.update { it + treeUri }
                    out
                }
                is ScanOutcome.Ok -> {
                    _foldersWithError.update { it - treeUri }
                    val existing = dao.videosInFolder(treeUri)
                    val (upsert, _) = diffVideos(existing, out.found)
                    dao.applyScan(
                        treeUri = treeUri,
                        upsert = upsert,
                        keepUris = out.found.map { it.documentUri },
                        scannedAt = System.currentTimeMillis(),
                    )
                    out
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "rescan failed for $treeUri", t)
            ScanOutcome.Ok(emptyList())
        } finally {
            _scanning.update { it - treeUri }
        }
    }

    suspend fun rescanAll() {
        dao.allFolders().forEach { rescan(it.treeUri) }
    }

    companion object {
        @Volatile private var instance: LibraryRepository? = null
        fun get(context: Context): LibraryRepository =
            instance ?: synchronized(this) {
                instance ?: LibraryRepository(
                    LumenDatabase.get(context).library(),
                    context.applicationContext,
                ).also { instance = it }
            }
    }
}
