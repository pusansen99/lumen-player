package com.lumen.player.library.data

import androidx.room.ColumnInfo

/**
 * A [LibraryVideo] with its resume state left-joined from `playback_history`.
 * The `h_*` columns are null when the user has never played this file.
 */
data class LibraryVideoRow(
    val documentUri: String,
    val folderTreeUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val relativePath: String,
    val showKey: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val metadataId: Long?,
    @ColumnInfo(name = "h_positionMs") val hPositionMs: Long?,
    @ColumnInfo(name = "h_durationMs") val hDurationMs: Long?,
    @ColumnInfo(name = "h_finished") val hFinished: Int?,
    @ColumnInfo(name = "h_thumbnailPath") val hThumbnailPath: String?,
) {
    val positionMs: Long get() = hPositionMs ?: 0L
    val durationMs: Long get() = hDurationMs ?: 0L
    val finished: Boolean get() = (hFinished ?: 0) != 0
    val thumbnailPath: String? get() = hThumbnailPath
}
