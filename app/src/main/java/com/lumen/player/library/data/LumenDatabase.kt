package com.lumen.player.library.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackHistoryEntry::class],
    version = 1,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {

    abstract fun history(): HistoryDao

    companion object {
        @Volatile
        private var instance: LumenDatabase? = null

        fun get(context: Context): LumenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LumenDatabase::class.java,
                    "lumen.db",
                ).build().also { instance = it }
            }
    }
}
