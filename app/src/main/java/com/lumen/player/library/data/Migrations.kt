package com.lumen.player.library.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 (playback_history only) -> v2: adds the folder-library tables. Additive; no data change. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `library_folder` (" +
                "`treeUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, `lastScannedAt` INTEGER NOT NULL, " +
                "`videoCount` INTEGER NOT NULL, PRIMARY KEY(`treeUri`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `library_video` (" +
                "`documentUri` TEXT NOT NULL, `folderTreeUri` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                "`lastModified` INTEGER NOT NULL, `relativePath` TEXT NOT NULL, " +
                "`showKey` TEXT, `seasonNumber` INTEGER, `episodeNumber` INTEGER, " +
                "`metadataId` INTEGER, PRIMARY KEY(`documentUri`), " +
                "FOREIGN KEY(`folderTreeUri`) REFERENCES `library_folder`(`treeUri`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_video_folderTreeUri` ON `library_video` (`folderTreeUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_video_showKey` ON `library_video` (`showKey`)")
    }
}
