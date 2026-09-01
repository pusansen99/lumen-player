package com.lumen.player.library.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A device folder the user added to the library via the system folder picker (SAF tree uri). */
@Entity(tableName = "library_folder")
data class LibraryFolder(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    /** Epoch ms of the last completed scan; 0 until the first scan finishes. */
    val lastScannedAt: Long,
    /** Video count from the last scan; the home card subtitle. */
    val videoCount: Int,
)

/** One video file discovered inside a [LibraryFolder]. */
@Entity(
    tableName = "library_video",
    foreignKeys = [ForeignKey(
        entity = LibraryFolder::class,
        parentColumns = ["treeUri"],
        childColumns = ["folderTreeUri"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("folderTreeUri"), Index("showKey")],
)
data class LibraryVideo(
    @PrimaryKey val documentUri: String,
    val folderTreeUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    /** Path under the tree root, '/'-joined, excluding the file name. Empty for a root-level file. */
    val relativePath: String,
    /** Normalised show name when an episode pattern matched; null means "treat as a movie". */
    val showKey: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    /** Phase 2b foreign key into a metadata table. Always null in 2a. */
    val metadataId: Long? = null,
)
