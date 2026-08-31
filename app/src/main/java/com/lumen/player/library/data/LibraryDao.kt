package com.lumen.player.library.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // --- folders ---
    @Upsert suspend fun upsertFolder(folder: LibraryFolder)

    @Query("DELETE FROM library_folder WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)

    @Query("SELECT * FROM library_folder ORDER BY displayName COLLATE NOCASE")
    fun observeFolders(): Flow<List<LibraryFolder>>

    @Query("SELECT * FROM library_folder WHERE treeUri = :treeUri")
    suspend fun folder(treeUri: String): LibraryFolder?

    @Query("SELECT * FROM library_folder WHERE treeUri = :treeUri")
    fun observeFolder(treeUri: String): Flow<LibraryFolder?>

    @Query("UPDATE library_folder SET lastScannedAt = :at, videoCount = :count WHERE treeUri = :treeUri")
    suspend fun setFolderScanned(treeUri: String, at: Long, count: Int)

    // --- videos ---
    @Upsert suspend fun upsertVideos(videos: List<LibraryVideo>)

    @Query("SELECT * FROM library_video WHERE folderTreeUri = :treeUri")
    suspend fun videosInFolder(treeUri: String): List<LibraryVideo>

    @Query("DELETE FROM library_video WHERE folderTreeUri = :treeUri AND documentUri NOT IN (:keepUris)")
    suspend fun deleteVideosNotIn(treeUri: String, keepUris: List<String>)

    @Query(
        "SELECT v.documentUri, v.folderTreeUri, v.displayName, v.sizeBytes, v.lastModified, " +
            "v.relativePath, v.showKey, v.seasonNumber, v.episodeNumber, v.metadataId, " +
            "h.positionMs AS h_positionMs, h.durationMs AS h_durationMs, " +
            "h.finished AS h_finished, h.thumbnailPath AS h_thumbnailPath " +
            "FROM library_video v " +
            "LEFT JOIN playback_history h ON h.uri = v.documentUri " +
            "WHERE v.folderTreeUri = :treeUri"
    )
    fun observeFolderRows(treeUri: String): Flow<List<LibraryVideoRow>>

    @Transaction
    suspend fun applyScan(
        treeUri: String,
        upsert: List<LibraryVideo>,
        keepUris: List<String>,
        scannedAt: Long,
    ) {
        if (upsert.isNotEmpty()) upsertVideos(upsert)
        deleteVideosNotIn(treeUri, keepUris)
        setFolderScanned(treeUri, scannedAt, keepUris.size)
    }
}
