package com.lumen.player.library

import android.content.Context
import android.util.Log
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

private const val TAG = "HistoryRepository"

/** Records and exposes per-video playback history. Mirrors [com.lumen.player.player.PlayerPrefs.get]. */
class HistoryRepository private constructor(private val dao: HistoryDao) {

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
        return resumePositionForEntry(dao.find(uri))
    }

    /** Fire-and-forget. Applies the finished rule. Never throws into playback. */
    fun updatePosition(rawUri: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 0L) return
        scope.launch {
            runCatching {
                val uri = normalizeMediaUri(rawUri)
                val existing = dao.find(uri) ?: return@launch
                val effectiveDuration = if (durationMs > 0L) durationMs else existing.durationMs
                dao.upsert(
                    existing.copy(
                        positionMs = positionMs,
                        durationMs = effectiveDuration,
                        finished = isFinished(positionMs, effectiveDuration),
                        lastPlayedAt = System.currentTimeMillis(),
                    ),
                )
            }.onFailure { Log.w(TAG, "updatePosition failed", it) }
        }
    }

    suspend fun updateThumbnail(rawUri: String, path: String) {
        val uri = normalizeMediaUri(rawUri)
        val existing = dao.find(uri) ?: return
        dao.upsert(existing.copy(thumbnailPath = path))
    }

    suspend fun forget(rawUri: String) = dao.delete(normalizeMediaUri(rawUri))
    suspend fun setFinished(rawUri: String, finished: Boolean) =
        dao.setFinished(normalizeMediaUri(rawUri), finished)
    suspend fun restart(rawUri: String) = dao.restart(normalizeMediaUri(rawUri))

    companion object {
        @Volatile
        private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(LumenDatabase.get(context).history())
                    .also { instance = it }
            }
    }
}
