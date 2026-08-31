package com.lumen.player.library.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Upsert
    suspend fun upsert(entry: PlaybackHistoryEntry)

    @Query("SELECT * FROM playback_history WHERE uri = :uri")
    suspend fun find(uri: String): PlaybackHistoryEntry?

    // SQL mirror of qualifiesForContinueWatching(); callers pass NEAR_EDGE_MS so the
    // threshold isn't duplicated as a literal here.
    @Query(
        "SELECT * FROM playback_history " +
            "WHERE finished = 0 AND positionMs > :minPositionMs " +
            "ORDER BY lastPlayedAt DESC LIMIT 30",
    )
    fun observeContinueWatching(minPositionMs: Long = NEAR_EDGE_MS): Flow<List<PlaybackHistoryEntry>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntry>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<PlaybackHistoryEntry>>

    @Query("SELECT uri FROM playback_history")
    suspend fun allUris(): List<String>

    @Query("DELETE FROM playback_history WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM playback_history")
    suspend fun clear()

    @Query(
        "UPDATE playback_history " +
            "SET positionMs = :positionMs, durationMs = :durationMs, " +
            "finished = :finished, lastPlayedAt = :lastPlayedAt " +
            "WHERE uri = :uri",
    )
    suspend fun updateProgress(
        uri: String,
        positionMs: Long,
        durationMs: Long,
        finished: Boolean,
        lastPlayedAt: Long,
    )

    @Query("UPDATE playback_history SET thumbnailPath = :path WHERE uri = :uri")
    suspend fun updateThumbnailPath(uri: String, path: String)

    @Query("UPDATE playback_history SET finished = :finished WHERE uri = :uri")
    suspend fun setFinished(uri: String, finished: Boolean)

    @Query("UPDATE playback_history SET positionMs = 0, finished = 0 WHERE uri = :uri")
    suspend fun restart(uri: String)
}
