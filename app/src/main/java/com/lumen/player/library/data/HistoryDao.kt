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

    // Mirror of qualifiesForContinueWatching(): finished = 0 AND positionMs > 5000.
    @Query(
        "SELECT * FROM playback_history " +
            "WHERE finished = 0 AND positionMs > 5000 " +
            "ORDER BY lastPlayedAt DESC LIMIT 30",
    )
    fun observeContinueWatching(): Flow<List<PlaybackHistoryEntry>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntry>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<PlaybackHistoryEntry>>

    @Query("DELETE FROM playback_history WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM playback_history")
    suspend fun clear()

    @Query("UPDATE playback_history SET finished = :finished WHERE uri = :uri")
    suspend fun setFinished(uri: String, finished: Boolean)

    @Query("UPDATE playback_history SET positionMs = 0, finished = 0 WHERE uri = :uri")
    suspend fun restart(uri: String)
}
