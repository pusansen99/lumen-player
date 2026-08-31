package com.lumen.player.library

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.lumen.player.library.data.HistoryDao
import com.lumen.player.library.data.LumenDatabase
import com.lumen.player.library.data.PlaybackHistoryEntry
import com.lumen.player.library.data.SourceType
import com.lumen.player.library.data.isFinished
import com.lumen.player.library.data.normalizeMediaUri
import com.lumen.player.library.data.resumePositionForEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "HistoryRepository"

/** Records and exposes per-video playback history. Mirrors [com.lumen.player.player.PlayerPrefs.get]. */
class HistoryRepository private constructor(
    private val dao: HistoryDao,
    private val appContext: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val continueWatching: Flow<List<PlaybackHistoryEntry>> = dao.observeContinueWatching()
    val all: Flow<List<PlaybackHistoryEntry>> = dao.observeAll()
    fun recent(limit: Int): Flow<List<PlaybackHistoryEntry>> = dao.observeRecent(limit)

    /**
     * Marks the start of a playback session for [rawUri]. Creates the row if new, otherwise bumps
     * `lastPlayedAt` and keeps the stored position. Returns where playback should resume (0 for a
     * new or finished video).
     */
    suspend fun startSession(
        rawUri: String,
        sourceType: SourceType,
        titleHint: String,
        hasPersistedPermission: Boolean,
    ): Long {
        val uri = normalizeMediaUri(rawUri)
        val now = System.currentTimeMillis()
        val existing = dao.find(uri)
        if (existing == null) {
            dao.upsert(
                PlaybackHistoryEntry(
                    uri = uri,
                    sourceType = sourceType.name,
                    title = titleHint.ifBlank { uri },
                    positionMs = 0L,
                    durationMs = 0L,
                    lastPlayedAt = now,
                    finished = false,
                    thumbnailPath = null,
                    hasPersistedPermission = hasPersistedPermission,
                ),
            )
        } else {
            dao.upsert(
                existing.copy(
                    lastPlayedAt = now,
                    // Keep the best title we have; upgrade a URL-derived title if a real hint arrives.
                    title = if (titleHint.isNotBlank() && existing.title == existing.uri) {
                        titleHint
                    } else {
                        existing.title
                    },
                    hasPersistedPermission = hasPersistedPermission || existing.hasPersistedPermission,
                ),
            )
        }
        // The upsert above never touches positionMs/finished, so the pre-write row is authoritative.
        return resumePositionForEntry(existing)
    }

    /** Fire-and-forget. Applies the finished rule. Never throws into playback. */
    fun updatePosition(rawUri: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 0L) return
        scope.launch {
            runCatching {
                val uri = normalizeMediaUri(rawUri)
                val existing = dao.find(uri) ?: return@launch
                val effectiveDuration = if (durationMs > 0L) durationMs else existing.durationMs
                // Targeted UPDATE: a concurrent updateThumbnail() can't be clobbered by a stale copy.
                dao.updateProgress(
                    uri = uri,
                    positionMs = positionMs,
                    durationMs = effectiveDuration,
                    finished = isFinished(positionMs, effectiveDuration),
                    lastPlayedAt = System.currentTimeMillis(),
                )
            }.onFailure { Log.w(TAG, "updatePosition failed", it) }
        }
    }

    suspend fun updateThumbnail(rawUri: String, path: String) {
        // Targeted UPDATE: no read-modify-write, so a concurrent updatePosition() can't clobber it.
        dao.updateThumbnailPath(normalizeMediaUri(rawUri), path)
    }

    /** Drops the row and, with it, the persistable read grant and the cached poster JPEG. */
    suspend fun forget(rawUri: String) {
        val uri = normalizeMediaUri(rawUri)
        releasePersistableGrant(uri)
        runCatching { File(File(appContext.filesDir, THUMB_DIR), thumbFileName(uri)).delete() }
        dao.delete(uri)
    }

    /** Wipes every row, releasing all persistable read grants and deleting the whole thumb cache. */
    suspend fun clearAll() {
        dao.allUris().forEach { releasePersistableGrant(it) }
        runCatching { File(appContext.filesDir, THUMB_DIR).deleteRecursively() }
        dao.clear()
    }

    /** Best-effort release of a `content://` read grant taken when the video was first opened. */
    private fun releasePersistableGrant(normalizedUri: String) {
        val uri = normalizedUri.toUri()
        if (uri.scheme != "content") return
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    suspend fun setFinished(rawUri: String, finished: Boolean) =
        dao.setFinished(normalizeMediaUri(rawUri), finished)
    suspend fun restart(rawUri: String) = dao.restart(normalizeMediaUri(rawUri))

    companion object {
        @Volatile
        private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(
                    LumenDatabase.get(context).history(),
                    context.applicationContext,
                ).also { instance = it }
            }
    }
}
