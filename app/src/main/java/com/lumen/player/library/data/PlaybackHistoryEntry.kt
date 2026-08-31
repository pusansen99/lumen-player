package com.lumen.player.library.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Distance from the end within which a video counts as "finished" and drops out of Continue Watching. */
const val NEAR_EDGE_MS = 5_000L

/**
 * One row per distinct video the user has played. The primary key is [normalizeMediaUri] of the
 * source URI, so the same video resumes whether it was opened from the play bar or an external intent.
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntry(
    @PrimaryKey val uri: String,
    /** [SourceType] name. */
    val sourceType: String,
    /** Display title: file name, URL host, or (Phase 2) a matched TMDB title. */
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    /** Epoch milliseconds of the most recent play; the sort key for Continue Watching and history. */
    val lastPlayedAt: Long,
    val finished: Boolean,
    /** Absolute path of a cached JPEG frame under filesDir/thumbs, or null if none was captured. */
    val thumbnailPath: String?,
    /** `content://` only: whether a persistable read grant was taken. false => the URI may be dead. */
    val hasPersistedPermission: Boolean,
    /** Phase 2: foreign key into `tmdb_metadata`. Always null in Phase 1. */
    val metadataId: Long? = null,
)

/** True when [positionMs] is within [NEAR_EDGE_MS] of a known [durationMs]. Unknown duration => false. */
fun isFinished(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs > durationMs - NEAR_EDGE_MS

/** Continue Watching shows unfinished rows the user is more than [NEAR_EDGE_MS] into. */
fun qualifiesForContinueWatching(entry: PlaybackHistoryEntry): Boolean =
    !entry.finished && entry.positionMs > NEAR_EDGE_MS

/** Where playback should start for a (possibly absent) history row: 0 for new or finished videos. */
fun resumePositionForEntry(entry: PlaybackHistoryEntry?): Long =
    if (entry == null || entry.finished) 0L else entry.positionMs
