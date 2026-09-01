# Local Media Library — Phase 2a (Folder Browsing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Point Lumen at device folders via the system folder picker and browse their videos as a poster grid — TV files grouped show → season → episode, movies flat — with resume state joined in from the Phase 1 history. Fully offline.

**Architecture:** Room DB goes v1 → v2 (adds `library_folder` + `library_video`, one hand-written tested migration). A `LibraryRepository` singleton owns SAF folder add/remove and runs `FolderScanner` (a BFS over `DocumentsContract` on `Dispatchers.IO`) on folder-add and manual rescan. A new `FolderScreen` renders `groupFolder(rows)` in a `LazyVerticalGrid`; `DetailSheet` offers Resume / Play-from-start. Coil 3 replaces Phase 1's hand-rolled bitmap loader and also decodes video frames for un-played files.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.2, Jetpack Compose (BOM 2026.08.00), AndroidX Media3 1.11.0, Room 2.8.4 + KSP, Coil 3.6.0 (`coil-compose`, `coil-network-okhttp`, `coil-video`), Robolectric 4.16.1 (test-only), DataStore 1.1.1, JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-01-local-media-library-phase-2a-design.md`

## Global Constraints

- Phase 2a code under `com.lumen.player.library` — `data/`, `scan/`, `ui/`.
- `minSdk = 36`, `targetSdk = 37`, `compileSdk = 37` — unchanged.
- Version from git tags; never hand-edit `app/build.gradle.kts` version logic.
- Unit tests: pure JVM JUnit4 under `app/src/test/java/...`, `org.junit.Assert.*` static imports, style of `app/src/test/java/com/lumen/player/library/*`. **Robolectric is allowed ONLY for `LibraryMigrationTest`.**
- No new signing/keystore literals in tracked files.
- Cinematic-dark UI: bg `#0B0B0D`, accent `#4C8DFF`, secondary text `#A1A1AA`. Reuse the colour vals already in `HistoryCards.kt` (`Accent`, `Surface`, `TextPrimary`, `TextSecondary`).
- Every task: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` green before commit (exceptions noted per task). The final task additionally runs `clean :app:assembleDebug :app:assembleRelease :app:lintDebug`.
- Branch `feat/library-folders` (already created, spec committed on it). Every task commits. `main` is protected — final step is a PR, owner merges.
- Commit messages, code, comments, KDoc: normal English. Commit trailer: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Test-count baseline entering this plan: **47** unit tests pass on `feat/library-folders` HEAD.

---

## File structure

**Created:**

| Path | Responsibility |
|---|---|
| `app/src/main/java/com/lumen/player/library/data/LibraryEntities.kt` | `LibraryFolder`, `LibraryVideo` `@Entity` data classes |
| `app/src/main/java/com/lumen/player/library/data/LibraryVideoRow.kt` | flat join POJO (`library_video` + `playback_history`) |
| `app/src/main/java/com/lumen/player/library/data/LibraryDao.kt` | `@Dao` for folders + videos + the resume-join query + `applyScan` |
| `app/src/main/java/com/lumen/player/library/data/Migrations.kt` | `MIGRATION_1_2` |
| `app/src/main/java/com/lumen/player/library/scan/FolderScanner.kt` | `FolderScanner.scan`, `ScanOutcome`, `episodeHints`, `EpisodeHint`, `diffVideos`, `DiffResult` |
| `app/src/main/java/com/lumen/player/library/LibraryRepository.kt` | singleton: `addFolder` / `removeFolder` / `rescan` / `rescanAll`, `folders` / `scanning` / `foldersWithError` flows, `folderRows(treeUri)` |
| `app/src/main/java/com/lumen/player/library/ui/LumenImageLoader.kt` | app `ImageLoader` factory (memory + disk cache + `VideoFrameDecoder`) |
| `app/src/main/java/com/lumen/player/library/ui/VideoPoster.kt` | `videoPosterModel(row)` (pure) + a small `AsyncImage` wrapper with the `Movie` placeholder |
| `app/src/main/java/com/lumen/player/library/ui/FolderGrouping.kt` | `groupFolder`, `FolderContents`, `Show`, `Season` |
| `app/src/main/java/com/lumen/player/library/ui/FolderScreen.kt` | the grid screen |
| `app/src/main/java/com/lumen/player/library/ui/DetailSheet.kt` | per-video bottom panel |
| `app/src/test/java/com/lumen/player/library/LibraryMigrationTest.kt` | Robolectric v1→v2 |
| `app/src/test/java/com/lumen/player/library/EpisodeHintsTest.kt` | filename/path → show/season/episode |
| `app/src/test/java/com/lumen/player/library/FolderScannerTest.kt` | BFS over a fake `ContentResolver` |
| `app/src/test/java/com/lumen/player/library/DiffVideosTest.kt` | add/remove/change/unchanged, `metadataId` carry |
| `app/src/test/java/com/lumen/player/library/FolderGroupingTest.kt` | grouping + ordering |
| `app/src/test/java/com/lumen/player/library/VideoPosterModelTest.kt` | poster model resolution order |
| `app/schemas/com.lumen.player.library.data.LumenDatabase/2.json` | generated, committed |

**Modified:**

| Path | Change |
|---|---|
| `gradle/libs.versions.toml` | add `coil`, `robolectric` versions + libs |
| `app/build.gradle.kts` | Coil + Robolectric deps, `unitTests.isIncludeAndroidResources = true` |
| `app/src/main/java/com/lumen/player/library/data/LumenDatabase.kt` | v2, three entities, `.addMigrations(MIGRATION_1_2)`, `abstract fun library(): LibraryDao` |
| `app/src/main/java/com/lumen/player/library/ui/HistoryCards.kt` | delete `rememberBitmap` + `LruCache`; `Poster` uses `AsyncImage` |
| `app/src/main/java/com/lumen/player/MainActivity.kt` | `setSingletonImageLoaderFactory { LumenImageLoader.get(it) }` |
| `app/src/main/java/com/lumen/player/player/PlayerScreen.kt` | `HomeRoute.Folder`, `folderRouteUri` state, `Folder ->` routing branch, pass `onOpenFolder` to `LibraryScreen` |
| `app/src/main/java/com/lumen/player/library/ui/LibraryScreen.kt` | "Folders" section: add-folder launcher, folder rows, long-press rescan/remove; new `onOpenFolder` param |
| `README.md` | Features table: Library row mentions folder browsing |

---

### Task 1: Add Coil 3 + Robolectric to the build

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (dependencies block ~line 128–160; `testOptions` block ~line 100)

**Interfaces:**
- Consumes: nothing.
- Produces: `libs.coil.compose`, `libs.coil.network.okhttp`, `libs.coil.video`, `libs.robolectric` available; `unitTests.isIncludeAndroidResources = true`.

- [ ] **Step 1: Add to the version catalog**

`gradle/libs.versions.toml`, under `[versions]`:

```toml
coil = "3.6.0"
robolectric = "4.16.1"
```

under `[libraries]`:

```toml
coil-compose        = { group = "io.coil-kt.coil3", name = "coil-compose",        version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
coil-video          = { group = "io.coil-kt.coil3", name = "coil-video",          version.ref = "coil" }
robolectric         = { group = "org.robolectric",  name = "robolectric",         version.ref = "robolectric" }
```

- [ ] **Step 2: Wire dependencies**

`app/build.gradle.kts`, in `dependencies { }` after `ksp(libs.androidx.room.compiler)`:

```kotlin
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
```

after `testImplementation(libs.org.json)`:

```kotlin
    testImplementation(libs.robolectric)
```

- [ ] **Step 3: Enable Android resources for Robolectric**

`app/build.gradle.kts`, the existing `testOptions` block currently reads:

```kotlin
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
```

Change it to:

```kotlin
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
```

- [ ] **Step 4: Verify resolution + release build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E "coil3|robolectric"`
Expected: `io.coil-kt.coil3:coil-compose:3.6.0`, `:coil-video:3.6.0`, `:coil-network-okhttp:3.6.0` present; `org.robolectric:robolectric` present only on test configs.

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleRelease`
Expected: BUILD SUCCESSFUL; 47 tests pass; release APK builds (Coil ships consumer ProGuard rules — if R8 fails with a Coil `ClassNotFoundException`, add to `app/proguard-rules.pro`:
`-keep class coil3.** { *; }` and `-dontwarn coil3.**`, re-run, and note it in the report).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Coil 3 + Robolectric for the folder library"
```

---

### Task 2: `LibraryFolder` + `LibraryVideo` entities, DB v2, `MIGRATION_1_2`, `LibraryDao`, schema export

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/data/LibraryEntities.kt`
- Create: `app/src/main/java/com/lumen/player/library/data/LibraryVideoRow.kt`
- Create: `app/src/main/java/com/lumen/player/library/data/LibraryDao.kt`
- Create: `app/src/main/java/com/lumen/player/library/data/Migrations.kt`
- Modify: `app/src/main/java/com/lumen/player/library/data/LumenDatabase.kt`
- Create (generated, commit): `app/schemas/com.lumen.player.library.data.LumenDatabase/2.json`

**Interfaces:**
- Consumes: `PlaybackHistoryEntry` (existing).
- Produces:
  - `LibraryFolder(treeUri, displayName, addedAt, lastScannedAt, videoCount)` — `@PrimaryKey treeUri: String`.
  - `LibraryVideo(documentUri, folderTreeUri, displayName, sizeBytes, lastModified, relativePath, showKey: String?, seasonNumber: Int?, episodeNumber: Int?, metadataId: Long? = null)` — `@PrimaryKey documentUri: String`, FK to `library_folder(treeUri)` `ON DELETE CASCADE`, indices on `folderTreeUri` and `showKey`.
  - `LibraryVideoRow` — flat POJO, one per `library_video` row + nullable joined `playback_history` columns; computed `positionMs: Long`, `durationMs: Long`, `finished: Boolean`, `thumbnailPath: String?`.
  - `LibraryDao` (see Step 3 for the full method set).
  - `MIGRATION_1_2: Migration`.
  - `LumenDatabase` version 2, `abstract fun library(): LibraryDao`, `.addMigrations(MIGRATION_1_2)`.

- [ ] **Step 1: Entities**

`LibraryEntities.kt`:

```kotlin
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
```

- [ ] **Step 2: Join POJO**

`LibraryVideoRow.kt`:

```kotlin
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
```

- [ ] **Step 3: DAO**

`LibraryDao.kt`:

```kotlin
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
```

- [ ] **Step 4: Migration**

`Migrations.kt`:

```kotlin
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
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_video_folderTreeUri` ON `library_video` (`folderTreeUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_video_showKey` ON `library_video` (`showKey`)")
    }
}
```

- [ ] **Step 5: Database**

`LumenDatabase.kt` — replace the `@Database` annotation and `get()`:

```kotlin
@Database(
    entities = [PlaybackHistoryEntry::class, LibraryFolder::class, LibraryVideo::class],
    version = 2,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {

    abstract fun history(): HistoryDao
    abstract fun library(): LibraryDao

    companion object {
        @Volatile
        private var instance: LumenDatabase? = null

        fun get(context: Context): LumenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LumenDatabase::class.java,
                    "lumen.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
```

Add the import `import com.lumen.player.library.data.MIGRATION_1_2`? — same package, no import needed.

- [ ] **Step 6: Generate + reconcile schema**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:kspDebugKotlin`
Expected: BUILD SUCCESSFUL; `app/schemas/com.lumen.player.library.data.LumenDatabase/2.json` written.

Open `2.json`. For `library_folder` and `library_video`, read the generator's `createSql`. **Compare it character-for-character against the `execSQL` strings in `Migrations.kt`** (ignoring the `IF NOT EXISTS` the migration adds and Room's `${TABLE_NAME}` placeholder which renders to the real name). Column order, types, `NOT NULL`, the FK clause, and both index `createSql` entries must match. Adjust `Migrations.kt` to match `2.json` exactly if they differ, then re-run `kspDebugKotlin` is not needed (migration isn't processed by KSP) — but re-read to confirm.

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; 47 tests still pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/data/ app/schemas
git commit -m "feat: Room v2 — library_folder + library_video + MIGRATION_1_2"
```

---

### Task 3: `LibraryMigrationTest` (Robolectric)

**Files:**
- Create: `app/src/test/java/com/lumen/player/library/LibraryMigrationTest.kt`

**Interfaces:**
- Consumes: `LumenDatabase`, `MIGRATION_1_2`, `LibraryDao`, `HistoryDao` (Task 2).
- Produces: nothing (test only).

- [ ] **Step 1: Write the test**

```kotlin
package com.lumen.player.library

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
@Config(manifest = Config.NONE, sdk = [34])
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
```

> `MigrationTestHelper` needs `androidx.room:room-testing` and
> `androidx.test:core` + `androidx.test:runner` on the **test**
> classpath. Add to `app/build.gradle.kts` in this task:
> ```kotlin
> testImplementation(libs.androidx.room.testing)   // add catalog entry: androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
> testImplementation("androidx.test:core:1.6.1")
> testImplementation("androidx.test:runner:1.6.2")
> ```
> Prefer catalog entries for the `androidx.test` ones too
> (`androidx-test-core`, `androidx-test-runner`) — pick current stable
> versions if 1.6.1 / 1.6.2 fail to resolve.
> If `MigrationTestHelper` proves unworkable under Robolectric in this
> repo, fall back to: build v1 by executing the `1.json` `createSql`
> against a `Room.inMemoryDatabaseBuilder`-less raw
> `SupportSQLiteDatabase` from `FrameworkSQLiteOpenHelperFactory`,
> call `MIGRATION_1_2.migrate(db)` directly, and assert via the same
> PRAGMA queries. Report which path was taken.

- [ ] **Step 2: Run it**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.LibraryMigrationTest"`
Expected: PASS (1 test). First run downloads the Robolectric android-all jar — allow time.

Run the full suite: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest`
Expected: 48 tests pass (47 + 1).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/lumen/player/library/LibraryMigrationTest.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "test: Robolectric migration test for Room v1 -> v2"
```

---

### Task 4: `episodeHints` (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/scan/FolderScanner.kt` (this task adds only `EpisodeHint` + `episodeHints`; later tasks extend the file)
- Create: `app/src/test/java/com/lumen/player/library/EpisodeHintsTest.kt`

**Interfaces:**
- Produces:
  - `data class EpisodeHint(val showKey: String?, val seasonNumber: Int?, val episodeNumber: Int?)`
  - `fun episodeHints(fileName: String, pathSegments: List<String>): EpisodeHint`
    - `pathSegments` = the folder names from the tree root down to (but not including) the file, outermost first.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.lumen.player.library

import com.lumen.player.library.scan.episodeHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeHintsTest {

    private fun hint(name: String, path: List<String> = emptyList()) = episodeHints(name, path)

    @Test fun sxxexx() {
        val h = hint("Show.Name.S02E10.1080p.WEB.mkv", listOf("Show Name"))
        assertEquals(2, h.seasonNumber); assertEquals(10, h.episodeNumber)
        assertEquals("show name", h.showKey)
    }

    @Test fun lowerAndSeparators() {
        assertEquals(1 to 2, hint("show s1e2.mkv").let { it.seasonNumber to it.episodeNumber })
        assertEquals(1 to 2, hint("show S01.E02.mkv").let { it.seasonNumber to it.episodeNumber })
        assertEquals(1 to 2, hint("show S01_E02.mkv").let { it.seasonNumber to it.episodeNumber })
    }

    @Test fun xFormat() {
        val h = hint("The Show - 3x04 - Title.mkv", listOf("The Show"))
        assertEquals(3, h.seasonNumber); assertEquals(4, h.episodeNumber)
        assertEquals("the show", h.showKey)
    }

    @Test fun seasonFolderFallback() {
        val h = hint("Episode 4.mkv", listOf("The Show", "Season 3"))
        assertEquals(3, h.seasonNumber); assertEquals(4, h.episodeNumber)
        assertEquals("the show", h.showKey)   // grandparent, since parent is "Season 3"
    }

    @Test fun seasonFolderNoEpisodeNumber() {
        val h = hint("random title.mkv", listOf("The Show", "Season 03"))
        assertEquals(3, h.seasonNumber); assertNull(h.episodeNumber)
        assertEquals("the show", h.showKey)
    }

    @Test fun specials() {
        val h = hint("Show S00E01.mkv", listOf("Show"))
        assertEquals(0, h.seasonNumber); assertEquals(1, h.episodeNumber)
    }

    @Test fun plainMovieIsNull() {
        val h = hint("The Movie (2019) 1080p BluRay.mkv", listOf("Movies"))
        assertNull(h.showKey); assertNull(h.seasonNumber); assertNull(h.episodeNumber)
    }

    @Test fun showKeyNormalisation() {
        assertEquals("mr robot", hint("Mr. Robot - S01E01.mkv", listOf("Mr.  Robot!")).showKey)
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`unresolved reference: episodeHints`).

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.EpisodeHintsTest"`

- [ ] **Step 3: Implement**

`FolderScanner.kt` (new file, this content only for now):

```kotlin
package com.lumen.player.library.scan

data class EpisodeHint(
    val showKey: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

private val SXXEXX = Regex("""[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})""")
private val NXM = Regex("""\b(\d{1,2})x(\d{1,3})\b""")
private val SEASON_DIR = Regex("""(?i)^season[ ._-]?(\d{1,3})$""")
private val LEADING_EP = Regex("""(?i)(?:^|[ ._-])(?:e|ep|episode)[ ._-]?(\d{1,3})\b""")

/** Lowercase, collapse any run of non-alphanumerics to one space, trim. */
private fun normaliseShowKey(raw: String): String =
    raw.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()

/**
 * Best-effort show / season / episode from a video file name and the folder names above it.
 * [pathSegments] is outermost-first and excludes the file. `showKey == null` => treat as a movie.
 */
fun episodeHints(fileName: String, pathSegments: List<String>): EpisodeHint {
    val base = fileName.substringBeforeLast('.')

    SXXEXX.find(base)?.let { m ->
        return EpisodeHint(showKeyFrom(base, pathSegments, m.range.first), m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }
    NXM.find(base)?.let { m ->
        return EpisodeHint(showKeyFrom(base, pathSegments, m.range.first), m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }
    // Season folder fallback
    val seasonDirIdx = pathSegments.indexOfLast { SEASON_DIR.matches(it.trim()) }
    if (seasonDirIdx >= 0) {
        val season = SEASON_DIR.find(pathSegments[seasonDirIdx].trim())!!.groupValues[1].toInt()
        val ep = LEADING_EP.find(base)?.groupValues?.get(1)?.toInt()
        val showSeg = pathSegments.getOrNull(seasonDirIdx - 1) ?: pathSegments.getOrNull(seasonDirIdx)
        return EpisodeHint(showSeg?.let(::normaliseShowKey), season, ep)
    }
    return EpisodeHint(null, null, null)
}

private fun showKeyFrom(base: String, pathSegments: List<String>, patternStart: Int): String {
    val parent = pathSegments.lastOrNull()
    if (parent != null && !SEASON_DIR.matches(parent.trim())) return normaliseShowKey(parent)
    val grandparent = pathSegments.getOrNull(pathSegments.lastIndex - 1)
    if (grandparent != null) return normaliseShowKey(grandparent)
    // no usable folder: use the file name up to the pattern
    return normaliseShowKey(base.substring(0, patternStart))
}
```

- [ ] **Step 4: Run — expect PASS** (8 tests). Then full suite: expect **56** (48 + 8).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/scan/FolderScanner.kt \
        app/src/test/java/com/lumen/player/library/EpisodeHintsTest.kt
git commit -m "feat: episodeHints — filename/path to show/season/episode"
```

---

### Task 5: `diffVideos` (pure, TDD)

**Files:**
- Modify: `app/src/main/java/com/lumen/player/library/scan/FolderScanner.kt` (append)
- Create: `app/src/test/java/com/lumen/player/library/DiffVideosTest.kt`

**Interfaces:**
- Consumes: `LibraryVideo` (Task 2).
- Produces:
  - `data class DiffResult(val upsert: List<LibraryVideo>, val deleteUris: List<String>)`
  - `fun diffVideos(existing: List<LibraryVideo>, found: List<LibraryVideo>): DiffResult`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.lumen.player.library

import com.lumen.player.library.data.LibraryVideo
import com.lumen.player.library.scan.diffVideos
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffVideosTest {

    private fun v(
        uri: String, size: Long = 10, mtime: Long = 100, path: String = "",
        show: String? = null, season: Int? = null, ep: Int? = null,
        name: String = "n", meta: Long? = null,
    ) = LibraryVideo(uri, "tree", name, size, mtime, path, show, season, ep, meta)

    @Test fun addedRowsUpserted() {
        val r = diffVideos(emptyList(), listOf(v("a"), v("b")))
        assertEquals(setOf("a", "b"), r.upsert.map { it.documentUri }.toSet())
        assertEquals(emptyList<String>(), r.deleteUris)
    }

    @Test fun removedRowsDeleted() {
        val r = diffVideos(listOf(v("a"), v("b")), listOf(v("a")))
        assertEquals(emptyList<String>(), r.upsert.map { it.documentUri })
        assertEquals(listOf("b"), r.deleteUris)
    }

    @Test fun unchangedRowsUntouched() {
        val r = diffVideos(listOf(v("a")), listOf(v("a")))
        assertEquals(emptyList<String>(), r.upsert.map { it.documentUri })
        assertEquals(emptyList<String>(), r.deleteUris)
    }

    @Test fun sizeOrMtimeOrPathOrEpisodeChangeUpserts() {
        assertEquals(listOf("a"), diffVideos(listOf(v("a", size = 10)), listOf(v("a", size = 20))).upsert.map { it.documentUri })
        assertEquals(listOf("a"), diffVideos(listOf(v("a", mtime = 1)), listOf(v("a", mtime = 2))).upsert.map { it.documentUri })
        assertEquals(listOf("a"), diffVideos(listOf(v("a", path = "x")), listOf(v("a", path = "y"))).upsert.map { it.documentUri })
        assertEquals(listOf("a"), diffVideos(listOf(v("a", ep = 1)), listOf(v("a", ep = 2))).upsert.map { it.documentUri })
        assertEquals(listOf("a"), diffVideos(listOf(v("a", name = "old")), listOf(v("a", name = "new"))).upsert.map { it.documentUri })
    }

    @Test fun metadataIdCarriedForwardOnChange() {
        val r = diffVideos(listOf(v("a", size = 10, meta = 42L)), listOf(v("a", size = 20, meta = null)))
        assertEquals(42L, r.upsert.single().metadataId)
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement** — append to `FolderScanner.kt`:

```kotlin
import com.lumen.player.library.data.LibraryVideo

data class DiffResult(val upsert: List<LibraryVideo>, val deleteUris: List<String>)

/**
 * Compares a folder's stored rows against a fresh scan.
 * `upsert` = new + changed rows (carrying forward the existing `metadataId`).
 * `deleteUris` = stored `documentUri`s the scan no longer found.
 * Unchanged rows appear in neither list.
 */
fun diffVideos(existing: List<LibraryVideo>, found: List<LibraryVideo>): DiffResult {
    val byUri = existing.associateBy { it.documentUri }
    val foundUris = HashSet<String>(found.size)
    val upsert = ArrayList<LibraryVideo>()
    for (f in found) {
        foundUris += f.documentUri
        val old = byUri[f.documentUri]
        if (old == null) {
            upsert += f
        } else if (
            old.sizeBytes != f.sizeBytes ||
            old.lastModified != f.lastModified ||
            old.relativePath != f.relativePath ||
            old.displayName != f.displayName ||
            old.showKey != f.showKey ||
            old.seasonNumber != f.seasonNumber ||
            old.episodeNumber != f.episodeNumber
        ) {
            upsert += f.copy(metadataId = old.metadataId)
        }
    }
    val deleteUris = existing.map { it.documentUri }.filter { it !in foundUris }
    return DiffResult(upsert, deleteUris)
}
```

- [ ] **Step 4: Run — expect PASS** (5). Full suite: **61**.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/scan/FolderScanner.kt \
        app/src/test/java/com/lumen/player/library/DiffVideosTest.kt
git commit -m "feat: diffVideos — scan vs stored folder rows"
```

---

### Task 6: `FolderScanner.scan` (fake `ContentResolver`, TDD)

**Files:**
- Modify: `app/src/main/java/com/lumen/player/library/scan/FolderScanner.kt` (append `ScanOutcome` + `FolderScanner`)
- Create: `app/src/test/java/com/lumen/player/library/FolderScannerTest.kt`

**Interfaces:**
- Consumes: `LibraryFolder`, `LibraryVideo`, `episodeHints`.
- Produces:
  - `sealed interface ScanOutcome { data class Ok(val found: List<LibraryVideo>) : ScanOutcome; data object PermissionLost : ScanOutcome }`
  - `object FolderScanner { suspend fun scan(resolver: ContentResolver, folder: LibraryFolder): ScanOutcome }`
  - internal `fun childrenUriFor(treeUri: Uri, parentDocId: String): Uri` (thin wrapper over `DocumentsContract.buildChildDocumentsUriUsingTree`) so the test can predict the uris it must answer.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.lumen.player.library

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract.Document
import com.lumen.player.library.data.LibraryFolder
import com.lumen.player.library.scan.FolderScanner
import com.lumen.player.library.scan.ScanOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FolderScannerTest {

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
    )

    private fun cursor(rows: List<Array<Any?>>): MatrixCursor =
        MatrixCursor(projection).also { c -> rows.forEach { c.addRow(it) } }

    private fun dir(id: String, name: String) = arrayOf<Any?>(id, name, Document.MIME_TYPE_DIR, 0L, 0L)
    private fun file(id: String, name: String, size: Long = 10, mtime: Long = 1) =
        arrayOf<Any?>(id, name, "video/x-matroska", size, mtime)
    private fun other(id: String, name: String) = arrayOf<Any?>(id, name, "text/plain", 1L, 1L)

    private val folder = LibraryFolder("content://tree/root", "root", 0, 0, 0)

    /** Wire a resolver whose query() answers a documentId -> cursor map, keyed by the child-doc-id in the uri. */
    private fun resolverFor(byDocId: Map<String, MatrixCursor?>): ContentResolver {
        val r = mock<ContentResolver>()
        whenever(r.query(any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenAnswer { inv ->
            val uri = inv.getArgument<Uri>(0)
            // childrenUri encodes the parent doc id as the last path segment
            val docId = uri.lastPathSegment ?: ""
            byDocId[docId] ?: byDocId["*root*"]  // "root" for the tree-root children uri
        }
        return r
    }

    @Test fun findsFilesAtRootAndNested() = runBlocking {
        val r = resolverFor(mapOf(
            "*root*" to cursor(listOf(file("f1", "Movie (2019).mkv"), dir("d1", "Show"))),
            "d1" to cursor(listOf(dir("d2", "Season 1"))),
            "d2" to cursor(listOf(file("f2", "Show S01E02.mkv"), other("x1", "notes.txt"))),
        ))
        val out = FolderScanner.scan(r, folder)
        assertTrue(out is ScanOutcome.Ok)
        val found = (out as ScanOutcome.Ok).found
        assertEquals(setOf("Movie (2019).mkv", "Show S01E02.mkv"), found.map { it.displayName }.toSet())
        val ep = found.first { it.displayName == "Show S01E02.mkv" }
        assertEquals("show", ep.showKey); assertEquals(1, ep.seasonNumber); assertEquals(2, ep.episodeNumber)
        assertEquals("Show/Season 1", ep.relativePath)
        val movie = found.first { it.displayName.startsWith("Movie") }
        assertEquals("", movie.relativePath); assertEquals(null, movie.showKey)
    }

    @Test fun rootQueryNullIsPermissionLost() = runBlocking {
        val r = resolverFor(mapOf("*root*" to null))
        assertEquals(ScanOutcome.PermissionLost, FolderScanner.scan(r, folder))
    }

    @Test fun deepQueryNullSkipsSubtree() = runBlocking {
        val r = resolverFor(mapOf(
            "*root*" to cursor(listOf(dir("d1", "A"), file("f1", "root.mkv"))),
            "d1" to null,
        ))
        val out = FolderScanner.scan(r, folder) as ScanOutcome.Ok
        assertEquals(listOf("root.mkv"), out.found.map { it.displayName })
    }

    @Test fun nonVideoSkipped() = runBlocking {
        val r = resolverFor(mapOf("*root*" to cursor(listOf(other("x", "a.txt"), file("f", "b.mp4")))))
        val out = FolderScanner.scan(r, folder) as ScanOutcome.Ok
        assertEquals(listOf("b.mp4"), out.found.map { it.displayName })
    }
}
```

> The fake uses Mockito-Kotlin. Add to the catalog + build:
> `mockito-kotlin = { group = "org.mockito.kotlin", name = "mockito-kotlin", version = "5.4.0" }` and
> `mockito-inline`/`mockito-core` transitively via it; `testImplementation(libs.mockito.kotlin)`.
> `MatrixCursor` and `Uri` are android.jar stubs — under plain
> `testDebugUnitTest` with `isReturnDefaultValues = true` they throw.
> **Therefore this test class also runs under Robolectric**:
> `@RunWith(RobolectricTestRunner::class) @Config(manifest = Config.NONE, sdk = [34])`.
> That is a second Robolectric class; acceptable — `FolderScanner`
> touches `MatrixCursor`, `Uri`, `DocumentsContract` which need real
> android classes. Keep `episodeHints` / `diffVideos` tests pure (they
> already are).

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement** — append to `FolderScanner.kt`:

```kotlin
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ScanOutcome {
    data class Ok(val found: List<LibraryVideo>) : ScanOutcome
    data object PermissionLost : ScanOutcome
}

private const val TAG = "FolderScanner"
private const val MAX_DEPTH = 12
private const val MAX_FILES = 20_000
private val VIDEO_EXT = setOf("mkv", "mp4", "m4v", "webm", "mov", "avi", "ts", "m3u8", "mpd")

internal fun childrenUriFor(treeUri: Uri, parentDocId: String): Uri =
    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

object FolderScanner {

    suspend fun scan(resolver: ContentResolver, folder: LibraryFolder): ScanOutcome =
        withContext(Dispatchers.IO) {
            val treeUri = folder.treeUri.toUri()
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            val found = ArrayList<LibraryVideo>()
            val visited = HashSet<String>()
            val queue = ArrayDeque<Pair<String, List<String>>>()   // (documentId, pathSegments)
            queue += rootId to emptyList()
            var first = true

            while (queue.isNotEmpty()) {
                val (docId, segments) = queue.removeFirst()
                if (!visited.add(docId)) continue
                if (segments.size > MAX_DEPTH) continue
                val childrenUri = childrenUriFor(treeUri, docId)
                val cursor = runCatching {
                    resolver.query(childrenUri, projection, null, null, null)
                }.getOrNull()
                if (cursor == null) {
                    if (first) return@withContext ScanOutcome.PermissionLost
                    else continue
                }
                first = false
                cursor.use { c ->
                    val idI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (c.moveToNext()) {
                        val childId = c.getString(idI) ?: continue
                        val name = c.getString(nameI) ?: continue
                        val mime = c.getString(mimeI) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue += childId to (segments + name)
                        } else if (isVideo(mime, name)) {
                            if (found.size >= MAX_FILES) {
                                Log.w(TAG, "hit MAX_FILES for ${folder.treeUri}")
                                return@use
                            }
                            val hint = episodeHints(name, segments)
                            found += LibraryVideo(
                                documentUri = DocumentsContract
                                    .buildDocumentUriUsingTree(treeUri, childId).toString(),
                                folderTreeUri = folder.treeUri,
                                displayName = name,
                                sizeBytes = if (c.isNull(sizeI)) 0L else c.getLong(sizeI),
                                lastModified = if (c.isNull(modI)) 0L else c.getLong(modI),
                                relativePath = segments.joinToString("/"),
                                showKey = hint.showKey,
                                seasonNumber = hint.seasonNumber,
                                episodeNumber = hint.episodeNumber,
                            )
                        }
                    }
                }
            }
            ScanOutcome.Ok(found)
        }

    private fun isVideo(mime: String, name: String): Boolean =
        mime.startsWith("video/") || name.substringAfterLast('.', "").lowercase() in VIDEO_EXT
}
```

> **Note for the test**: `childrenUriFor` builds the real
> `DocumentsContract` child uri; its `lastPathSegment` is the encoded
> `parentDocId`, which is what the test's `resolverFor` keys on
> (`"*root*"` maps to `rootId` = whatever `getTreeDocumentId` returns
> for `content://tree/root`; the test should compute that once and use
> it as the key, or make `resolverFor` fall back to `*root*` when the
> docId is unknown — the given test does the latter).

- [ ] **Step 4: Run — expect PASS** (4). Full suite: **65** (61 + 4).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/scan/FolderScanner.kt \
        app/src/test/java/com/lumen/player/library/FolderScannerTest.kt \
        gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: FolderScanner — BFS walk of a SAF tree"
```

---

### Task 7: `LibraryRepository`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/LibraryRepository.kt`

**Interfaces:**
- Consumes: `LumenDatabase.get(context).library()`, `FolderScanner`, `diffVideos`, `LibraryDao`, `LibraryFolder`, `LibraryVideoRow`, `ScanOutcome`.
- Produces `LibraryRepository`:
  - `companion object { fun get(context: Context): LibraryRepository }` (double-checked singleton, `applicationContext`, mirrors `HistoryRepository`).
  - `val folders: Flow<List<LibraryFolder>>`
  - `val scanning: StateFlow<Set<String>>`
  - `val foldersWithError: StateFlow<Set<String>>`
  - `fun folderRows(treeUri: String): Flow<List<LibraryVideoRow>>`
  - `fun observeFolder(treeUri: String): Flow<LibraryFolder?>`
  - `sealed interface AddFolderResult { data class Ok(val treeUri: String) : ...; data object PermissionDenied : ... }`
  - `suspend fun addFolder(treeUri: Uri): AddFolderResult`
  - `suspend fun removeFolder(treeUri: String)`
  - `suspend fun rescan(treeUri: String): ScanOutcome`
  - `suspend fun rescanAll()`

- [ ] **Step 1: Implement**

```kotlin
package com.lumen.player.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.lumen.player.library.data.LibraryDao
import com.lumen.player.library.data.LibraryFolder
import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.data.LumenDatabase
import com.lumen.player.library.scan.FolderScanner
import com.lumen.player.library.scan.ScanOutcome
import com.lumen.player.library.scan.diffVideos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "LibraryRepository"

/** Owns SAF folders and runs [FolderScanner]. Mirrors [HistoryRepository.get]. */
class LibraryRepository private constructor(
    private val dao: LibraryDao,
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutexes = HashMap<String, Mutex>()
    private fun mutexFor(treeUri: String) = synchronized(mutexes) { mutexes.getOrPut(treeUri) { Mutex() } }

    private val _scanning = MutableStateFlow<Set<String>>(emptySet())
    val scanning: StateFlow<Set<String>> = _scanning.asStateFlow()

    private val _foldersWithError = MutableStateFlow<Set<String>>(emptySet())
    val foldersWithError: StateFlow<Set<String>> = _foldersWithError.asStateFlow()

    val folders: Flow<List<LibraryFolder>> = dao.observeFolders()
    fun folderRows(treeUri: String): Flow<List<LibraryVideoRow>> = dao.observeFolderRows(treeUri)
    fun observeFolder(treeUri: String): Flow<LibraryFolder?> = dao.observeFolder(treeUri)

    sealed interface AddFolderResult {
        data class Ok(val treeUri: String) : AddFolderResult
        data object PermissionDenied : AddFolderResult
    }

    suspend fun addFolder(treeUri: Uri): AddFolderResult {
        val ok = runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (!ok) return AddFolderResult.PermissionDenied

        val key = treeUri.toString()
        val name = runCatching { DocumentFile.fromTreeUri(appContext, treeUri)?.name }.getOrNull()
            ?: treeUri.lastPathSegment?.substringAfterLast('/') ?: key
        dao.upsertFolder(LibraryFolder(key, name, System.currentTimeMillis(), 0L, 0))
        scope.launch { rescan(key) }
        return AddFolderResult.Ok(key)
    }

    suspend fun removeFolder(treeUri: String) {
        dao.deleteFolder(treeUri)   // FK cascade drops library_video rows
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                treeUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        _foldersWithError.update { it - treeUri }
    }

    suspend fun rescan(treeUri: String): ScanOutcome = mutexFor(treeUri).withLock {
        val folder = dao.folder(treeUri) ?: return ScanOutcome.Ok(emptyList())
        _scanning.update { it + treeUri }
        try {
            when (val out = FolderScanner.scan(appContext.contentResolver, folder)) {
                is ScanOutcome.PermissionLost -> {
                    _foldersWithError.update { it + treeUri }
                    out
                }
                is ScanOutcome.Ok -> {
                    _foldersWithError.update { it - treeUri }
                    val existing = dao.videosInFolder(treeUri)
                    val (upsert, _) = diffVideos(existing, out.found)
                    dao.applyScan(
                        treeUri = treeUri,
                        upsert = upsert,
                        keepUris = out.found.map { it.documentUri },
                        scannedAt = System.currentTimeMillis(),
                    )
                    out
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "rescan failed for $treeUri", t)
            ScanOutcome.Ok(emptyList())
        } finally {
            _scanning.update { it - treeUri }
        }
    }

    suspend fun rescanAll() {
        dao.observeFolders() // not collected; use a one-shot:
        // simplest: query current folders once
        val current = kotlinx.coroutines.flow.first(dao.observeFolders())
        current.forEach { rescan(it.treeUri) }
    }

    companion object {
        @Volatile private var instance: LibraryRepository? = null
        fun get(context: Context): LibraryRepository =
            instance ?: synchronized(this) {
                instance ?: LibraryRepository(
                    LumenDatabase.get(context).library(),
                    context.applicationContext,
                ).also { instance = it }
            }
    }
}
```

> `rescanAll` as written is awkward — replace the body with a clean
> one-shot read: add `@Query("SELECT * FROM library_folder") suspend fun allFolders(): List<LibraryFolder>`
> to `LibraryDao` and do `dao.allFolders().forEach { rescan(it.treeUri) }`.
> Make that DAO addition in this task.
> Add `androidx.documentfile:documentfile:1.0.1` to the catalog +
> `implementation` (`androidx-documentfile`), it is not currently a
> dependency.

- [ ] **Step 2: Compile check**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; 65 tests still pass (no new tests this task — repository is exercised by device QA + the pure units it composes).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/LibraryRepository.kt \
        app/src/main/java/com/lumen/player/library/data/LibraryDao.kt \
        gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: LibraryRepository — add/remove/rescan SAF folders"
```

---

### Task 8: Coil `ImageLoader` + wire into Compose

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/LumenImageLoader.kt`
- Modify: `app/src/main/java/com/lumen/player/MainActivity.kt`

**Interfaces:**
- Produces: `object LumenImageLoader { fun get(context: Context): ImageLoader }` — memoised.
- `MainActivity` calls `setSingletonImageLoaderFactory { LumenImageLoader.get(it) }` once, before `setContent`.

- [ ] **Step 1: Implement the loader**

```kotlin
package com.lumen.player.library.ui

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder

/** App-wide Coil loader: memory + disk cache, and video-frame decoding for `content://` video models. */
object LumenImageLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components { add(VideoFrameDecoder.Factory()) }
                .memoryCache { MemoryCache.Builder().maxSizePercent(context.applicationContext, 0.20).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("coil"))
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
                .also { instance = it }
        }
}
```

> Verify the exact Coil 3.6.0 API names at implementation time
> (`maxSizePercent`, `directory`, `components`); adjust to what the
> resolved artifacts expose. `context7` or the Coil 3 docs are the
> reference. Do not guess — check the resolved jar.

- [ ] **Step 2: Wire into `MainActivity`**

In `onCreate`, before `setContent { ... }`:

```kotlin
        coil3.compose.setSingletonImageLoaderFactory { LumenImageLoader.get(it) }
```

Add the import `import com.lumen.player.library.ui.LumenImageLoader` (or fully-qualify).

- [ ] **Step 3: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/LumenImageLoader.kt \
        app/src/main/java/com/lumen/player/MainActivity.kt
git commit -m "feat: app-wide Coil ImageLoader with video-frame decoding"
```

---

### Task 9: `videoPosterModel` + replace `rememberBitmap` in `HistoryCards`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/VideoPoster.kt`
- Create: `app/src/test/java/com/lumen/player/library/VideoPosterModelTest.kt`
- Modify: `app/src/main/java/com/lumen/player/library/ui/HistoryCards.kt`

**Interfaces:**
- Produces:
  - `fun videoPosterModel(row: LibraryVideoRow): Any?` — pure: `row.thumbnailPath ?: row.documentUri` (the `metadataId` branch is a 2b TODO; return the same for now).
  - `@Composable fun VideoPoster(model: Any?, modifier: Modifier, framePercent: Double = 0.15)` — an `AsyncImage` wrapper that falls back to the `Icons.Filled.Movie` placeholder on error/null.
- `HistoryCards.Poster` delegates to `VideoPoster`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.lumen.player.library

import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.ui.videoPosterModel
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPosterModelTest {

    private fun row(thumb: String?, uri: String = "content://doc/1") = LibraryVideoRow(
        documentUri = uri, folderTreeUri = "t", displayName = "n", sizeBytes = 0, lastModified = 0,
        relativePath = "", showKey = null, seasonNumber = null, episodeNumber = null, metadataId = null,
        hPositionMs = null, hDurationMs = null, hFinished = null, hThumbnailPath = thumb,
    )

    @Test fun prefersThumbnailPath() {
        assertEquals("/data/thumbs/a.jpg", videoPosterModel(row(thumb = "/data/thumbs/a.jpg")))
    }

    @Test fun fallsBackToDocumentUri() {
        assertEquals("content://doc/1", videoPosterModel(row(thumb = null)))
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement `VideoPoster.kt`**

```kotlin
package com.lumen.player.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import com.lumen.player.library.data.LibraryVideoRow

private val PosterSurface = Color(0xFF16161A)
private val PlaceholderTint = Color(0xFFA1A1AA)

/** thumbnailPath (a played video's cached frame) if present, else the document uri (Coil grabs a frame). */
fun videoPosterModel(row: LibraryVideoRow): Any? = row.thumbnailPath ?: row.documentUri

@Composable
fun VideoPoster(model: Any?, modifier: Modifier = Modifier, framePercent: Double = 0.15) {
    val context = LocalContext.current
    Box(modifier = modifier.background(PosterSurface), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Movie, contentDescription = null,
            tint = PlaceholderTint, modifier = Modifier.size(28.dp),
        )
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .videoFramePercent(framePercent)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
```

> The `Icon` under the `AsyncImage` shows through only while loading /
> on failure (the image draws over it opaque on success). If Coil 3.6.0
> exposes `videoFramePercent` under a different import, fix it; it is a
> `coil-video` extension.

- [ ] **Step 4: Rewrite `HistoryCards.Poster`**

In `HistoryCards.kt`: delete lines 3–4 (`import android.graphics.BitmapFactory`, `import android.util.LruCache`), 27–28 (`produceState` import — keep if used elsewhere; it is not), 33–34 (`ImageBitmap`, `asImageBitmap`), 39 (`import androidx.compose.foundation.Image`), 41–42 (`Dispatchers`, `withContext`), the `bitmapCache` val (line 49), and the whole `rememberBitmap` function (51–63). Replace the `Poster` composable (77–89) with:

```kotlin
@Composable
private fun Poster(path: String?, modifier: Modifier = Modifier) {
    VideoPoster(model = path, modifier = modifier)
}
```

(`VideoPoster` with a `String?` path model works — Coil treats a
`String` file path as a file; a null model shows the placeholder.)
Remove now-unused imports the compiler flags (`background`, `Box`,
`Alignment`, `ContentScale`, `Icon`, `Icons`, `Movie`, `size` may all
become unused in this file — let the compiler guide; keep any still
referenced by other composables in the file).

- [ ] **Step 5: Run tests + build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.VideoPosterModelTest"` → PASS (2).
Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL; **67** tests (65 + 2).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/VideoPoster.kt \
        app/src/main/java/com/lumen/player/library/ui/HistoryCards.kt \
        app/src/test/java/com/lumen/player/library/VideoPosterModelTest.kt
git commit -m "feat: Coil-backed VideoPoster; drop hand-rolled rememberBitmap"
```

---

### Task 10: `groupFolder` (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/FolderGrouping.kt`
- Create: `app/src/test/java/com/lumen/player/library/FolderGroupingTest.kt`

**Interfaces:**
- Consumes: `LibraryVideoRow`.
- Produces:
  - `data class FolderContents(val movies: List<LibraryVideoRow>, val shows: List<Show>)`
  - `data class Show(val showKey: String, val displayName: String, val episodeCount: Int, val seasons: List<Season>)`
  - `data class Season(val number: Int, val episodes: List<LibraryVideoRow>)`
  - `fun groupFolder(rows: List<LibraryVideoRow>): FolderContents`
  - `fun showDisplayName(showKey: String): String` — title-cases the normalised key (2b overrides with the real name).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.lumen.player.library

import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.ui.groupFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderGroupingTest {

    private fun row(
        name: String, show: String? = null, season: Int? = null, ep: Int? = null,
        uri: String = name,
    ) = LibraryVideoRow(
        documentUri = uri, folderTreeUri = "t", displayName = name, sizeBytes = 0, lastModified = 0,
        relativePath = "", showKey = show, seasonNumber = season, episodeNumber = ep, metadataId = null,
        hPositionMs = null, hDurationMs = null, hFinished = null, hThumbnailPath = null,
    )

    @Test fun moviesOnly_sortedCaseInsensitive() {
        val c = groupFolder(listOf(row("banana.mkv"), row("Apple.mkv")))
        assertEquals(listOf("Apple.mkv", "banana.mkv"), c.movies.map { it.displayName })
        assertEquals(emptyList<Any>(), c.shows)
    }

    @Test fun oneShow_seasonsAscending_specialsLast() {
        val c = groupFolder(listOf(
            row("e1", show = "the show", season = 2, ep = 1),
            row("sp", show = "the show", season = 0, ep = 1),
            row("e0", show = "the show", season = 1, ep = 1),
        ))
        assertEquals(1, c.shows.size)
        assertEquals(listOf(1, 2, 0), c.shows[0].seasons.map { it.number })
    }

    @Test fun episodesSortedByNumber_nullsLast() {
        val c = groupFolder(listOf(
            row("b", show = "s", season = 1, ep = null),
            row("a", show = "s", season = 1, ep = 3),
            row("c", show = "s", season = 1, ep = 1),
        ))
        assertEquals(listOf("c", "a", "b"), c.shows[0].seasons[0].episodes.map { it.displayName })
    }

    @Test fun twoShows_alphaOrder() {
        val c = groupFolder(listOf(
            row("x", show = "zebra", season = 1, ep = 1),
            row("y", show = "alpha", season = 1, ep = 1),
        ))
        assertEquals(listOf("Alpha", "Zebra"), c.shows.map { it.displayName })
    }

    @Test fun showWithoutSeason_bucketsIntoZero() {
        val c = groupFolder(listOf(row("x", show = "s", season = null, ep = 1)))
        assertEquals(listOf(0), c.shows[0].seasons.map { it.number })
    }

    @Test fun episodeCountAcrossSeasons() {
        val c = groupFolder(listOf(
            row("a", show = "s", season = 1, ep = 1),
            row("b", show = "s", season = 1, ep = 2),
            row("c", show = "s", season = 2, ep = 1),
        ))
        assertEquals(3, c.shows[0].episodeCount)
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement**

```kotlin
package com.lumen.player.library.ui

import com.lumen.player.library.data.LibraryVideoRow

data class FolderContents(val movies: List<LibraryVideoRow>, val shows: List<Show>)
data class Show(
    val showKey: String,
    val displayName: String,
    val episodeCount: Int,
    val seasons: List<Season>,
)
data class Season(val number: Int, val episodes: List<LibraryVideoRow>)

/** Title-cases a normalised show key ("the show" -> "The Show"). Phase 2b replaces with the TMDB name. */
fun showDisplayName(showKey: String): String =
    showKey.split(' ').filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private val ciName = Comparator<String> { a, b -> a.compareTo(b, ignoreCase = true) }

fun groupFolder(rows: List<LibraryVideoRow>): FolderContents {
    val movies = rows.filter { it.showKey == null }
        .sortedWith(compareBy(ciName) { it.displayName })

    val shows = rows.filter { it.showKey != null }
        .groupBy { it.showKey!! }
        .map { (key, showRows) ->
            val seasons = showRows.groupBy { it.seasonNumber ?: 0 }
                .map { (num, eps) ->
                    Season(
                        number = num,
                        episodes = eps.sortedWith(
                            compareBy<LibraryVideoRow> { it.episodeNumber ?: Int.MAX_VALUE }
                                .thenComparator { a, b -> ciName.compare(a.displayName, b.displayName) },
                        ),
                    )
                }
                .sortedWith(compareBy { if (it.number == 0) Int.MAX_VALUE else it.number })
            Show(
                showKey = key,
                displayName = showDisplayName(key),
                episodeCount = showRows.size,
                seasons = seasons,
            )
        }
        .sortedWith(compareBy(ciName) { it.displayName })

    return FolderContents(movies, shows)
}
```

- [ ] **Step 4: Run — expect PASS** (6). Full suite: **73**.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/FolderGrouping.kt \
        app/src/test/java/com/lumen/player/library/FolderGroupingTest.kt
git commit -m "feat: groupFolder — rows into movies + show/season/episode"
```

---

### Task 11: `DetailSheet`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/DetailSheet.kt`

**Interfaces:**
- Consumes: `LibraryVideoRow`, `videoPosterModel`, `VideoPoster`, `SourceType`, `HistoryRepository`, `formatTime` (from `com.lumen.player.player`).
- Produces:
  - `@Composable fun DetailSheet(row: LibraryVideoRow, onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Implement**

```kotlin
package com.lumen.player.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.Formatter
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.data.SourceType
import com.lumen.player.player.formatTime
import kotlinx.coroutines.launch

private val Scrim = Color(0xCC000000)
private val Panel = Color(0xFF121216)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

/** Bottom panel for a library video: poster, path, size, and Resume / Play-from-start. */
@Composable
fun DetailSheet(
    row: LibraryVideoRow,
    onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = row.episodeLabel()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Scrim),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Panel)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            ) { VideoPoster(model = videoPosterModel(row), modifier = Modifier) }

            Text(label, color = TextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.relativePath.isNotEmpty()) {
                Text(row.relativePath, color = TextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Text(Formatter.formatShortFileSize(context, row.sizeBytes), color = TextSecondary, fontSize = 11.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (row.positionMs > 0L && !row.finished) {
                    Button(onClick = {
                        onPlay(row.documentUri, label, SourceType.SAF_FILE, true)
                        onDismiss()
                    }) { Text("Resume from ${formatTime(row.positionMs)}") }
                }
                OutlinedButton(onClick = {
                    scope.launch { HistoryRepository.get(context).restart(row.documentUri) }
                    onPlay(row.documentUri, label, SourceType.SAF_FILE, true)
                    onDismiss()
                }) { Text("Play from start") }
            }
        }
    }
}

fun LibraryVideoRow.episodeLabel(): String {
    val s = seasonNumber; val e = episodeNumber
    return if (showKey != null && s != null && e != null) {
        "S%02dE%02d · %s".format(s, e, displayName)
    } else displayName
}
```

> `formatTime` is `internal`/`public` top-level in
> `com.lumen.player.player.ManagedExoPlayer.kt` — confirm its
> visibility; if `internal`, it is still visible from the same module,
> which this is. `HistoryRepository.restart` exists from Phase 1.

- [ ] **Step 2: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; 73 tests.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/DetailSheet.kt
git commit -m "feat: DetailSheet — per-video panel with Resume / Play from start"
```

---

### Task 12: `FolderScreen`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/FolderScreen.kt`

**Interfaces:**
- Consumes: `LibraryRepository`, `groupFolder`/`FolderContents`/`Show`/`Season`, `VideoPoster`/`videoPosterModel`, `DetailSheet`, `LibraryVideoRow`, `SourceType`.
- Produces:
  - `@Composable fun FolderScreen(treeUri: String, onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Implement**

```kotlin
package com.lumen.player.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.library.LibraryRepository
import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.data.SourceType
import kotlinx.coroutines.launch

private val Accent = Color(0xFF4C8DFF)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

@Composable
fun FolderScreen(
    treeUri: String,
    onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val repo = remember { LibraryRepository.get(context) }
    val scope = rememberCoroutineScope()

    val folder by repo.observeFolder(treeUri).collectAsState(initial = null)
    val rows by repo.folderRows(treeUri).collectAsState(initial = emptyList())
    val scanning by repo.scanning.collectAsState()
    val contents = remember(rows) { groupFolder(rows) }

    val expanded = rememberSaveable(
        saver = androidx.compose.runtime.saveable.mapSaver(
            save = { it.toMap() }, restore = { it.mapValues { e -> e.value as Boolean }.toMutableMap().let(::mutableStateMapOf).apply { putAll(it.mapValues { e -> e.value as Boolean }) } },
        ),
    ) { mutableStateMapOf<String, Boolean>() }

    var sheetRow by remember { mutableStateOf<LibraryVideoRow?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(folder?.displayName ?: "Folder", color = TextPrimary, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { repo.rescan(treeUri) } }) { Text("Rescan") }
        }

        val isScanning = treeUri in scanning
        when {
            rows.isEmpty() && isScanning ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rows.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No videos in this folder.", color = TextSecondary, fontSize = 13.sp)
                        TextButton(onClick = { scope.launch { repo.rescan(treeUri) } }) { Text("Rescan") }
                    }
                }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (contents.movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Movies") }
                    items(contents.movies, key = { it.documentUri }) { row ->
                        MovieTile(row, onClick = { sheetRow = row })
                    }
                }
                contents.shows.forEach { show ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "show:${show.showKey}") {
                        val open = expanded[show.showKey] == true
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { expanded[show.showKey] = !open }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null, tint = TextSecondary)
                            Text("${show.displayName}  ·  ${show.episodeCount} episodes",
                                color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    if (expanded[show.showKey] == true) {
                        show.seasons.forEach { season ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "s:${show.showKey}:${season.number}") {
                                Text(
                                    if (season.number == 0) "Specials" else "Season ${season.number}",
                                    color = TextSecondary, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                )
                            }
                            items(season.episodes, span = { GridItemSpan(maxLineSpan) },
                                key = { it.documentUri }) { row -> EpisodeRow(row, onClick = { sheetRow = row }) }
                        }
                    }
                }
            }
        }
    }

    sheetRow?.let { r ->
        DetailSheet(row = r, onPlay = onPlay, onDismiss = { sheetRow = null })
    }
}

@Composable private fun SectionHeader(text: String) =
    Text(text, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun MovieTile(row: LibraryVideoRow, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
            VideoPoster(model = videoPosterModel(row), modifier = Modifier)
            ResumeBar(row, Modifier.align(Alignment.BottomStart))
        }
        Text(row.displayName, color = TextPrimary, fontSize = 12.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EpisodeRow(row: LibraryVideoRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(6.dp))) {
            VideoPoster(model = videoPosterModel(row), modifier = Modifier)
            ResumeBar(row, Modifier.align(Alignment.BottomStart))
        }
        Text(row.episodeLabel(), color = TextPrimary, fontSize = 13.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ResumeBar(row: LibraryVideoRow, modifier: Modifier) {
    if (row.positionMs > 0L && !row.finished && row.durationMs > 0L) {
        LinearProgressIndicator(
            progress = { (row.positionMs.toFloat() / row.durationMs).coerceIn(0f, 1f) },
            color = Accent, trackColor = Color(0x33FFFFFF),
            modifier = modifier.fillMaxWidth().height(3.dp),
        )
    }
}
```

> The `rememberSaveable` map saver above is fiddly — if it does not
> compile cleanly, use the simpler
> `remember { mutableStateMapOf<String, Boolean>() }` (expansion state
> resets on process death; acceptable for v1) and note the
> simplification. `episodeLabel()` is the extension from Task 11.

- [ ] **Step 2: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; 73 tests.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/FolderScreen.kt
git commit -m "feat: FolderScreen — poster grid with show/season/episode groups"
```

---

### Task 13: Wire navigation + LibraryScreen "Folders" section

**Files:**
- Modify: `app/src/main/java/com/lumen/player/player/PlayerScreen.kt`
- Modify: `app/src/main/java/com/lumen/player/library/ui/LibraryScreen.kt`

**Interfaces:**
- Consumes: `FolderScreen` (Task 12), `LibraryRepository` (Task 7).
- Produces: `HomeRoute.Folder`; `LibraryScreen` gains `onOpenFolder: (treeUri: String) -> Unit`.

- [ ] **Step 1: `PlayerScreen.kt` — nav**

At the enum (line 80): `enum class HomeRoute { Library, History, Settings, Folder }`.

Near `var homeRoute by rememberSaveable ...` (line 127) add:
```kotlin
    var folderRouteUri by rememberSaveable { mutableStateOf<String?>(null) }
```

In the `when (homeRoute)` block (line 152), add a branch (and pass the new callback to `LibraryScreen`):
```kotlin
                HomeRoute.Library -> LibraryScreen(
                    // ...existing args...
                    onOpenHistory = { homeRoute = HomeRoute.History },
                    onOpenSettings = { homeRoute = HomeRoute.Settings },
                    onOpenFolder = { uri -> folderRouteUri = uri; homeRoute = HomeRoute.Folder },
                    // ...existing modifier...
                )
                // ...History, Settings unchanged...
                HomeRoute.Folder -> FolderScreen(
                    treeUri = folderRouteUri.orEmpty(),
                    onPlay = { value, label, type, hasPerm ->
                        // same body the Library onPlay uses to launch playback
                        if (value.startsWith("http", ignoreCase = true)) prefs.setLastUrl(value)
                        sourceUri = value; sourceLabel = label
                        sourceTypeName = type.name; hasPersistedPermission = hasPerm
                    },
                    onBack = { homeRoute = HomeRoute.Library },
                )
```
Add `import com.lumen.player.library.ui.FolderScreen`.
The `BackHandler(enabled = homeRoute != HomeRoute.Library) { homeRoute = HomeRoute.Library }` at line 151 already covers `Folder`.

> Match the exact `onPlay` lambda body to whatever the current
> `HomeRoute.Library -> LibraryScreen(onPlay = ...)` uses — copy it
> verbatim so folder playback and library playback behave identically.

- [ ] **Step 2: `LibraryScreen.kt` — signature + Folders section**

Add param to `fun LibraryScreen(...)` (after `onOpenSettings`):
```kotlin
    onOpenFolder: (treeUri: String) -> Unit,
```

Near the other `remember`/`collectAsState` at the top of the composable:
```kotlin
    val libraryRepo = remember { com.lumen.player.library.LibraryRepository.get(context) }
    val folders by libraryRepo.folders.collectAsState(emptyList())
    val scanningFolders by libraryRepo.scanning.collectAsState()
    val folderErrors by libraryRepo.foldersWithError.collectAsState()
    val addFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) scope.launch {
            when (libraryRepo.addFolder(uri)) {
                is com.lumen.player.library.LibraryRepository.AddFolderResult.PermissionDenied ->
                    android.widget.Toast.makeText(context,
                        "Couldn't get lasting access to that folder.", android.widget.Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }
    }
    var folderMenuUri by remember { mutableStateOf<String?>(null) }
    var confirmRemoveUri by remember { mutableStateOf<String?>(null) }
```
(`scope` = the existing `rememberCoroutineScope()` in the file; `ActivityResultContracts` / `rememberLauncherForActivityResult` are already imported for the "Open file" picker.)

In the `LazyColumn`, **after the "Recent" section and before the empty-state hint item**, add:
```kotlin
            if (folders.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Folders", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addFolderLauncher.launch(null) }) { Text("+ Add folder") }
                    }
                }
                items(folders, key = { it.treeUri }) { folder ->
                    val subtitle = when {
                        folder.treeUri in folderErrors -> "Can't read — long-press to remove"
                        folder.treeUri in scanningFolders -> "Scanning…"
                        folder.videoCount == 0 && folder.lastScannedAt > 0L -> "No videos"
                        folder.videoCount == 0 -> "Scanning…"
                        else -> "${folder.videoCount} videos"
                    }
                    Box {
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenFolder(folder.treeUri) },
                                    onLongClick = { folderMenuUri = folder.treeUri },
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = TextSecondary)
                            Column(Modifier.weight(1f)) {
                                Text(folder.displayName, color = TextPrimary, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        if (folderMenuUri == folder.treeUri) {
                            DropdownMenu(expanded = true, onDismissRequest = { folderMenuUri = null }) {
                                DropdownMenuItem(text = { Text("Rescan") }, onClick = {
                                    folderMenuUri = null
                                    scope.launch { libraryRepo.rescan(folder.treeUri) }
                                })
                                DropdownMenuItem(text = { Text("Remove folder") }, onClick = {
                                    folderMenuUri = null
                                    confirmRemoveUri = folder.treeUri
                                })
                            }
                        }
                    }
                }
            } else {
                // Folders section header with just the add button, so the entry point exists on a fresh install
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Folders", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addFolderLauncher.launch(null) }) { Text("+ Add folder") }
                    }
                }
            }
```

> Note: this changes the spec's "section hidden entirely when no
> folders" — instead the header + "+ Add folder" button always show,
> so there is a discoverable entry point. This is a deliberate
> improvement; note it in the report. The empty-state hint item that
> follows should only render when `continueWatching`, `recentOnly`
> **and** `folders` are all empty — update its `if` condition
> accordingly.

After the `LazyColumn`, add the confirm dialog:
```kotlin
    confirmRemoveUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRemoveUri = null },
            title = { Text("Remove this folder?") },
            text = { Text("Its videos leave the library. Watch history is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveUri = null
                    scope.launch { libraryRepo.removeFolder(uri) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveUri = null }) { Text("Cancel") } },
        )
    }
```

Add imports as the compiler flags: `androidx.compose.material.icons.filled.FolderOpen`,
`androidx.compose.material3.AlertDialog`, `androidx.compose.material3.DropdownMenu`,
`androidx.compose.material3.DropdownMenuItem`,
`androidx.compose.foundation.combinedClickable` (+ its `@OptIn(ExperimentalFoundationApi::class)` on `LibraryScreen`).

- [ ] **Step 3: Build + full suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL; 73 tests; lint clean.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lumen/player/player/PlayerScreen.kt \
        app/src/main/java/com/lumen/player/library/ui/LibraryScreen.kt
git commit -m "feat: Folders section on the home screen + FolderScreen route"
```

---

### Task 14: Full verification, README, device QA, PR

**Files:**
- Modify: `README.md`

**Interfaces:** none.

- [ ] **Step 1: README**

In the Features table, change the `Library` row to mention folders:

```markdown
| Library | Home screen "Continue watching" + full history (Room); plus **device folders** you add via the system picker — browsed as a poster grid, TV files grouped show → season → episode |
```

Add near the "Optional source-provided sidecars" section a short note:

```markdown
## Folder library

Add device folders from the home screen ("+ Add folder"). Lumen scans
them for video files (no `MediaStore`, no broad storage permission —
only the SAF grant for folders you pick) and shows them as a grid.
Metadata (real posters / titles from TMDB) lands in a later release.
```

- [ ] **Step 2: Clean verification**

Run:
```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew clean \
  :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --stacktrace
```
Expected: BUILD SUCCESSFUL. 73 unit tests, 0 failures. Lint clean
(`abortOnError = true`). Release APK builds (Coil consumer rules +
R8). If `assembleRelease` fails on a Coil or `coil-video` class, add
the minimal `-keep` / `-dontwarn` to `app/proguard-rules.pro` and note
it.

- [ ] **Step 3: Device QA (emulator, API 37)**

Install: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:installDebug`

Prep a folder on the device:
```
adb shell mkdir -p /sdcard/Movies/QA/Show/Season\ 01
# push a couple of small mp4s: one loose "Movie (2020).mp4", two "Show S01E01.mp4" / "Show S01E02.mp4"
```
(Generate clips with ffmpeg as in the Phase 1 QA; push with `adb push`.)

Verify:
- [ ] Upgrade path: install the pre-2a build (`v0.0.2` release APK), play a video, then `installDebug` this build → app opens, no crash, history intact, `lumen.db` at version 2.
- [ ] Home shows a "Folders" section header with "+ Add folder".
- [ ] "+ Add folder" → system tree picker → pick `/sdcard/Movies/QA` → row appears "Scanning…" → then "3 videos".
- [ ] Open the folder → grid: "Movie (2020).mp4" as a movie tile; a "Show · 2 episodes" header; expand it → "Season 1" → two episode rows "S01E01 · …" / "S01E02 · …".
- [ ] Frame thumbnails render for the un-played files (Coil `VideoFrameDecoder`).
- [ ] Tap a movie tile → `DetailSheet` (poster, path, size, "Play from start"). Play it, back out, reopen the sheet → "Resume from m:ss" now shown; tap it → resumes.
- [ ] A previously-played file shows its Phase 1 thumbnail (not a fresh frame) — play one via the URL bar first if needed, then confirm.
- [ ] Long-press the folder row → "Rescan" (add a file on device, rescan, grid grows) and "Remove folder" → confirm dialog → folder + its grid entries gone; a file you played from it is still in Continue Watching / History.
- [ ] Revoke access: `adb shell content call --uri ...` is awkward — instead remove the folder's persisted permission by uninstalling a co-owner, or delete `/sdcard/Movies/QA` and rescan → folder row shows "Can't read — long-press to remove"; existing rows retained (not wiped). If revocation can't be simulated cleanly, note it as covered only by `FolderScannerTest.rootQueryNullIsPermissionLost`.
- [ ] `ContinueWatchingCard` / `HistoryRow` thumbnails still render after the Coil swap (no regression).

- [ ] **Step 4: Commit + PR**

```bash
git add README.md app/proguard-rules.pro
git commit -m "docs: document the folder library; proguard keeps if any"
git push -u origin feat/library-folders
gh pr create --base main --head feat/library-folders \
  --title "Local media library — Phase 2a: folder browsing" \
  --body-file docs/superpowers/plans/phase-2a-pr-body.md   # write this file with the summary below
```

PR body summary: Room v2 + `library_folder`/`library_video` + tested
migration; `LibraryRepository` + `FolderScanner` (SAF BFS);
`FolderScreen` grid with show/season/episode grouping + `DetailSheet`;
Coil 3 replaces the hand-rolled bitmap loader and decodes frames for
un-played files. 73 unit tests (6 new suites + the Robolectric
migration test). Offline only — TMDB metadata is Phase 2b.

---

## Self-review

**Spec coverage:**

| Spec section | Task(s) |
|---|---|
| Dependencies (Coil 3, Robolectric) | 1 |
| §1 entities + `LibraryDao` + `LibraryVideoRow` | 2 |
| §1 `MIGRATION_1_2` + v2 + schema `2.json` | 2 |
| §1 `LibraryMigrationTest` (Robolectric) | 3 |
| §2 `episodeHints` | 4 |
| §2 `diffVideos` | 5 |
| §2 `FolderScanner.scan` (BFS, permission-lost, depth cap) | 6 |
| §2 `LibraryRepository` (add/remove/rescan, Mutex, `scanning`/`foldersWithError`) | 7 |
| §3 `LumenImageLoader` + `setSingletonImageLoaderFactory` | 8 |
| §3 `videoPosterModel` + `HistoryCards` `rememberBitmap` removal | 9 |
| §3 `VideoPoster` wrapper with placeholder | 9 |
| §4 `groupFolder` + ordering | 10 |
| §4 `DetailSheet` (Resume / Play-from-start) | 11 |
| §4 `FolderScreen` grid + expandable groups + states | 12 |
| §4 `HomeRoute.Folder` nav + LibraryScreen "Folders" section | 13 |
| README + clean verification + device QA + PR | 14 |

**Deviations from the spec, deliberate, flagged in-plan:**
- Folders section shows its header + "+ Add folder" even when empty (spec said hidden) — needed a discoverable entry point (Task 13).
- `FolderScannerTest` runs under Robolectric, not pure JVM — `MatrixCursor`/`Uri`/`DocumentsContract` need real android classes (Task 6). `episodeHints` / `diffVideos` / `groupFolder` / `videoPosterModel` tests stay pure.
- Adds `androidx.documentfile`, `mockito-kotlin`, `androidx.test:core`/`runner`, `androidx.room:room-testing` as test/impl deps not named in the spec's "Dependencies added" — all in service of the spec's stated components/tests; pinned in the tasks.

**Placeholder scan:** the `rememberSaveable` map-saver in Task 12 and the exact Coil 3.6.0 API names in Task 8 carry an explicit "verify at implementation time / fall back to X" instruction with a concrete fallback — not open TODOs.

**Type consistency:** `onPlay` lambda shape `(rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean)` is identical across `LibraryScreen`, `FolderScreen`, `DetailSheet`, and the `PlayerScreen` routing (Tasks 11–13). `LibraryVideoRow` field names (`documentUri`, `showKey`, `seasonNumber`, `episodeNumber`, `positionMs`, `durationMs`, `finished`, `thumbnailPath`) match between the POJO (Task 2), `groupFolder` (Task 10), `videoPosterModel` (Task 9), `DetailSheet` (Task 11), `FolderScreen` (Task 12). `ScanOutcome` / `AddFolderResult` / `DiffResult` each defined once. `applyScan(treeUri, upsert, keepUris, scannedAt)` — defined Task 2, called Task 7 with matching args.
