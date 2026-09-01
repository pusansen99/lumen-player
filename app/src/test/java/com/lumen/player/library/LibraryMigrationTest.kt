package com.lumen.player.library

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.lumen.player.library.data.LumenDatabase
import com.lumen.player.library.data.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [36])
class LibraryMigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        LumenDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_addsTables_keepsHistory() {
        // v1 schema comes from the exported 1.json bundled as a test asset by Room.
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO playback_history " +
                    "(uri, sourceType, title, positionMs, durationMs, lastPlayedAt, finished, " +
                    "thumbnailPath, hasPersistedPermission, metadataId) " +
                    "VALUES ('u1','URL','t',1000,2000,5,0,NULL,1,NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // new tables exist
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertTrue("library_folder" in names)
            assertTrue("library_video" in names)
        }
        // FK + indices on library_video
        db.query("PRAGMA foreign_key_list(`library_video`)").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("library_folder", c.getString(c.getColumnIndexOrThrow("table")))
        }
        db.query("PRAGMA index_list(`library_video`)").use { c ->
            val idx = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            assertTrue(idx.any { it.contains("folderTreeUri") })
            assertTrue(idx.any { it.contains("showKey") })
        }
        // history survived
        db.query("SELECT uri, positionMs FROM playback_history").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("u1", c.getString(0))
            assertEquals(1000L, c.getLong(1))
        }
        db.close()
    }
}
