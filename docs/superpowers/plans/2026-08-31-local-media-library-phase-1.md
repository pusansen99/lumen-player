# Local Media Library — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every play — typed URL, in-app file pick, or external `ACTION_VIEW` / `ACTION_SEND` — is recorded with its resume position and surfaced in a "Continue Watching" list on a new home screen; tapping a row resumes.

**Architecture:** Introduce Room (SQLite) as the library store, alongside the existing DataStore (kept for prefs only). One entity in Phase 1: `playback_history`. A `HistoryRepository` singleton (mirrors `PlayerPrefs.get(context)`) wraps the DAO. `PlayerContainer` swaps its `PlayerPrefs` position calls for the repository and adds lifecycle-driven position writes. The old `SourcePicker` becomes a `LibraryScreen` (Continue Watching rail + Recent list + compact play bar), with lightweight enum-based nav to `HistoryScreen` and `SettingsScreen` (TMDB key field, unwired in Phase 1).

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.2, Jetpack Compose (BOM 2026.08.00), AndroidX Media3 1.11.0, Room 2.8.4, KSP 2.3.11, DataStore Preferences 1.1.1, JUnit4 (pure-JVM unit tests, no Robolectric).

**Spec:** `docs/superpowers/specs/2026-08-31-local-media-library-design.md`

## Global Constraints

- Package root `com.lumen.player`; new code under `com.lumen.player.library`.
- `minSdk = 36`, `targetSdk = 37`, `compileSdk = 37` — unchanged.
- No new signing/keystore literals in tracked files.
- Version numbers come from git tags (`app/build.gradle.kts` `gitVersionName`) — never hand-edit a version.
- Unit tests are pure JVM under `app/src/test/java/...`, JUnit4, `org.junit.Assert.*` static imports (match `SkipSegmentsParseTest`). No Robolectric, no `androidTest` source set is added in this phase.
- `testOptions { unitTests.isReturnDefaultValues = true }` is already set — android.jar calls in test-reachable code return defaults, so keep Android-framework calls out of unit-tested functions.
- Cinematic-dark UI: background `#0B0B0D`, accent `#4C8DFF`, secondary text `#A1A1AA`. Reuse `com.lumen.player.ui.theme` and the color constants already in `PlayerControls.kt` where practical.
- Branch: `feat/local-media-library` (already created off `main`). Every task commits. Open a PR at the end — `main` is protected (PR + green `build` check, owner merges).
- Caveman mode is a session display style only; **all committed code, comments, KDoc, and commit messages are written in normal English.**

---

## File structure (Phase 1)

**Created:**

| Path | Responsibility |
|---|---|
| `app/src/main/java/com/lumen/player/library/data/MediaUri.kt` | `normalizeMediaUri(String): String`, `enum SourceType` |
| `app/src/main/java/com/lumen/player/library/data/PlaybackHistoryEntry.kt` | `@Entity` data class + pure rules: `isFinished`, `qualifiesForContinueWatching`, `resumePositionForEntry` |
| `app/src/main/java/com/lumen/player/library/data/HistoryDao.kt` | Room DAO |
| `app/src/main/java/com/lumen/player/library/data/LumenDatabase.kt` | `@Database`, singleton `get(context)` |
| `app/src/main/java/com/lumen/player/library/HistoryRepository.kt` | Singleton over `HistoryDao`: `startSession`, `updatePosition`, `forget`, `setFinished`, `restart`, observable flows |
| `app/src/main/java/com/lumen/player/library/Thumbnailer.kt` | `captureFrame(context, uri, atMs): String?`, `thumbFileName(uri): String` |
| `app/src/main/java/com/lumen/player/library/ui/HistoryCards.kt` | `rememberBitmap`, `ContinueWatchingCard`, `HistoryRow`, `HistoryItemMenu` |
| `app/src/main/java/com/lumen/player/library/ui/LibraryScreen.kt` | Home: header, play bar, Continue Watching rail, Recent list |
| `app/src/main/java/com/lumen/player/library/ui/HistoryScreen.kt` | Full history list, swipe-to-remove, Clear all |
| `app/src/main/java/com/lumen/player/library/ui/SettingsScreen.kt` | TMDB API key field (saves to DataStore; "Verify" disabled) |
| `app/src/test/java/com/lumen/player/library/MediaUriTest.kt` | tests for `normalizeMediaUri` |
| `app/src/test/java/com/lumen/player/library/PlaybackHistoryRulesTest.kt` | tests for the pure rules |
| `app/src/test/java/com/lumen/player/library/HistoryMigrationTest.kt` | tests for `legacyResumeKeyNames` |
| `app/schemas/com.lumen.player.library.data.LumenDatabase/1.json` | exported Room schema (generated, committed) |

**Modified:**

| Path | Change |
|---|---|
| `gradle/libs.versions.toml` | add `ksp`, `room` versions; room libs; `ksp` + `androidx-room` plugins |
| `app/build.gradle.kts` | apply `ksp` + `androidx.room` plugins; `room { schemaDirectory(...) }`; room deps |
| `app/src/main/java/com/lumen/player/player/PlayerPrefs.kt` | add `tmdbApiKey` flow + `setTmdbApiKey`; add `migrateLegacyResumeData()`; `legacyResumeKeyNames` helper |
| `app/src/main/java/com/lumen/player/player/PlayerScreen.kt` | thread `SourceType` + `hasPersistedPermission`; `PlayerContainer` uses `HistoryRepository`; lifecycle position writes; thumbnail capture; replace `SourcePicker` with `LibraryScreen` + `homeRoute` nav |
| `app/src/main/java/com/lumen/player/MainActivity.kt` | derive `SourceType`; `takePersistableUriPermission` for `content://`; pass through |
| `README.md` | Features table: mention Continue Watching / history |

---

### Task 1: Add Room + KSP to the build

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:1-5` (plugins), `:19-35` (android block), `:119-147` (dependencies)

**Interfaces:**
- Consumes: nothing.
- Produces: `libs.plugins.ksp`, `libs.plugins.androidx.room`, `libs.androidx.room.runtime`, `libs.androidx.room.ktx`, `libs.androidx.room.compiler` available to later tasks; Room schema export configured at `app/schemas`.

- [ ] **Step 1: Add versions, libraries, plugins to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
ksp = "2.3.11"
room = "2.8.4"
```

Under `[libraries]` add:

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

Under `[plugins]` add:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Apply the plugins**

In `app/build.gradle.kts`, the `plugins { }` block becomes:

```kotlin
plugins {
    // AGP 9 has built-in Kotlin support, so no kotlin-android plugin is applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}
```

- [ ] **Step 3: Configure the Room schema directory**

In `app/build.gradle.kts`, inside the `android { }` block (place it just after the `lint { }` block, before the closing brace of `android`):

```kotlin
    room {
        schemaDirectory("$projectDir/schemas")
    }
```

- [ ] **Step 4: Add the Room dependencies**

In `app/build.gradle.kts`, in `dependencies { }`, after the `implementation(libs.androidx.datastore.preferences)` line:

```kotlin
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
```

- [ ] **Step 5: Verify dependency resolution and build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:dependencies --configuration debugRuntimeClasspath` — expect `androidx.room:room-runtime:2.8.4` and `androidx.room:room-ktx:2.8.4` listed.

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no Room code yet — this only proves the plugins and deps load).

If it fails with a KSP/Kotlin compatibility error, set `ksp` in the catalog to the newest version listed at `https://github.com/google/ksp/releases` (KSP 2.x uses independent versioning and is Kotlin-version-tolerant; `2.3.11` is current at plan time) and re-run.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Room 2.8.4 + KSP for the media library store"
```

---

### Task 2: `normalizeMediaUri` and `SourceType`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/data/MediaUri.kt`
- Test: `app/src/test/java/com/lumen/player/library/MediaUriTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `fun normalizeMediaUri(raw: String): String`
  - `enum class SourceType { URL, SAF_FILE, EXTERNAL_VIEW, EXTERNAL_SEND }`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/lumen/player/library/MediaUriTest.kt`:

```kotlin
package com.lumen.player.library

import com.lumen.player.library.data.normalizeMediaUri
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUriTest {

    @Test fun trimsWhitespace() {
        assertEquals("https://x.com/a.mp4", normalizeMediaUri("  https://x.com/a.mp4\n"))
    }

    @Test fun dropsHttpFragment() {
        assertEquals(
            "https://x.com/a.m3u8",
            normalizeMediaUri("https://x.com/a.m3u8#t=42"),
        )
    }

    @Test fun lowercasesHttpSchemeAndHostKeepsPathCase() {
        assertEquals(
            "https://cdn.example.com/Movies/A.MP4",
            normalizeMediaUri("HTTPS://CDN.Example.COM/Movies/A.MP4"),
        )
    }

    @Test fun preservesHttpQuery() {
        assertEquals(
            "https://x.com/a.mp4?token=abc123",
            normalizeMediaUri("https://x.com/a.mp4?token=abc123"),
        )
    }

    @Test fun contentUriReturnedVerbatimApartFromTrim() {
        val c = "content://com.android.providers.media.documents/document/video%3A1000"
        assertEquals(c, normalizeMediaUri("  $c  "))
    }

    @Test fun fileUriReturnedVerbatimApartFromTrim() {
        assertEquals("file:///storage/emulated/0/Movies/A.mkv",
            normalizeMediaUri("file:///storage/emulated/0/Movies/A.mkv"))
    }

    @Test fun unknownSchemeReturnedVerbatim() {
        assertEquals("rtsp://host/stream", normalizeMediaUri("rtsp://host/stream"))
    }

    @Test fun isIdempotent() {
        val once = normalizeMediaUri("HTTP://Host.com/p?x=1#f")
        assertEquals(once, normalizeMediaUri(once))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.MediaUriTest"`
Expected: FAIL — unresolved reference `normalizeMediaUri`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/lumen/player/library/data/MediaUri.kt`:

```kotlin
package com.lumen.player.library.data

/** How a video reached the player. Stored as the enum name on [PlaybackHistoryEntry.sourceType]. */
enum class SourceType {
    /** Typed or pasted into the play bar. */
    URL,

    /** Picked in-app via the system document picker (persistable permission taken). */
    SAF_FILE,

    /** Handed in by another app through ACTION_VIEW ("Open with"). */
    EXTERNAL_VIEW,

    /** Handed in by another app through the share sheet (ACTION_SEND). */
    EXTERNAL_SEND,
}

/**
 * Canonical form of a media URI, used as the primary key for resume state.
 *
 * Must be stable across app sessions and identical whether the same video arrives
 * through the play bar or an external intent. For `http`/`https` the scheme and host
 * are lowercased and the fragment is dropped; path and query are kept as-is.
 * `content://`, `file://` and everything else are returned trimmed but otherwise verbatim.
 */
fun normalizeMediaUri(raw: String): String {
    val trimmed = raw.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return trimmed
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return trimmed

    val rest = trimmed.substring(schemeEnd + 3)
    val withoutFragment = rest.substringBefore('#')
    val slash = withoutFragment.indexOf('/')
    return if (slash < 0) {
        "$scheme://${withoutFragment.lowercase()}"
    } else {
        val host = withoutFragment.substring(0, slash).lowercase()
        val pathAndQuery = withoutFragment.substring(slash)
        "$scheme://$host$pathAndQuery"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.MediaUriTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/data/MediaUri.kt \
        app/src/test/java/com/lumen/player/library/MediaUriTest.kt
git commit -m "feat: normalizeMediaUri + SourceType for the library store"
```

---

### Task 3: `PlaybackHistoryEntry` entity and pure rules

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/data/PlaybackHistoryEntry.kt`
- Test: `app/src/test/java/com/lumen/player/library/PlaybackHistoryRulesTest.kt`

**Interfaces:**
- Consumes: `SourceType` (Task 2).
- Produces:
  - `@Entity(tableName = "playback_history") data class PlaybackHistoryEntry(uri: String, sourceType: String, title: String, positionMs: Long, durationMs: Long, lastPlayedAt: Long, finished: Boolean, thumbnailPath: String?, hasPersistedPermission: Boolean, metadataId: Long?)`
  - `const val NEAR_EDGE_MS = 5_000L`
  - `fun isFinished(positionMs: Long, durationMs: Long): Boolean`
  - `fun qualifiesForContinueWatching(entry: PlaybackHistoryEntry): Boolean`
  - `fun resumePositionForEntry(entry: PlaybackHistoryEntry?): Long`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/lumen/player/library/PlaybackHistoryRulesTest.kt`:

```kotlin
package com.lumen.player.library

import com.lumen.player.library.data.PlaybackHistoryEntry
import com.lumen.player.library.data.isFinished
import com.lumen.player.library.data.qualifiesForContinueWatching
import com.lumen.player.library.data.resumePositionForEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHistoryRulesTest {

    private fun entry(
        position: Long,
        duration: Long = 1_800_000L,
        finished: Boolean = false,
    ) = PlaybackHistoryEntry(
        uri = "u",
        sourceType = "URL",
        title = "t",
        positionMs = position,
        durationMs = duration,
        lastPlayedAt = 0L,
        finished = finished,
        thumbnailPath = null,
        hasPersistedPermission = true,
        metadataId = null,
    )

    @Test fun notFinishedInTheMiddle() {
        assertFalse(isFinished(positionMs = 900_000L, durationMs = 1_800_000L))
    }

    @Test fun finishedWithinFiveSecondsOfTheEnd() {
        assertTrue(isFinished(positionMs = 1_796_000L, durationMs = 1_800_000L))
    }

    @Test fun exactlyOnTheBoundaryIsNotFinished() {
        // positionMs == durationMs - NEAR_EDGE_MS -> strictly greater test fails -> not finished
        assertFalse(isFinished(positionMs = 1_795_000L, durationMs = 1_800_000L))
    }

    @Test fun unknownDurationIsNeverFinished() {
        assertFalse(isFinished(positionMs = 10_000L, durationMs = 0L))
        assertFalse(isFinished(positionMs = 10_000L, durationMs = -1L))
    }

    @Test fun continueWatchingNeedsUnfinishedAndPastFiveSeconds() {
        assertTrue(qualifiesForContinueWatching(entry(position = 6_000L)))
        assertFalse(qualifiesForContinueWatching(entry(position = 4_000L)))
        assertFalse(qualifiesForContinueWatching(entry(position = 6_000L, finished = true)))
    }

    @Test fun resumePositionIsZeroForNullOrFinished() {
        assertEquals(0L, resumePositionForEntry(null))
        assertEquals(0L, resumePositionForEntry(entry(position = 500_000L, finished = true)))
    }

    @Test fun resumePositionIsStoredPositionOtherwise() {
        assertEquals(500_000L, resumePositionForEntry(entry(position = 500_000L)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.PlaybackHistoryRulesTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/lumen/player/library/data/PlaybackHistoryEntry.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.PlaybackHistoryRulesTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/data/PlaybackHistoryEntry.kt \
        app/src/test/java/com/lumen/player/library/PlaybackHistoryRulesTest.kt
git commit -m "feat: playback_history entity + resume/finished rules"
```

---

### Task 4: `HistoryDao` and `LumenDatabase`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/data/HistoryDao.kt`
- Create: `app/src/main/java/com/lumen/player/library/data/LumenDatabase.kt`
- Create (generated, commit): `app/schemas/com.lumen.player.library.data.LumenDatabase/1.json`

**Interfaces:**
- Consumes: `PlaybackHistoryEntry` (Task 3).
- Produces:
  - `HistoryDao` with:
    - `suspend fun upsert(entry: PlaybackHistoryEntry)`
    - `suspend fun find(uri: String): PlaybackHistoryEntry?`
    - `suspend fun touch(uri: String, lastPlayedAt: Long)` — bumps only `lastPlayedAt`
    - `fun observeContinueWatching(): Flow<List<PlaybackHistoryEntry>>`
    - `fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntry>>`
    - `fun observeAll(): Flow<List<PlaybackHistoryEntry>>`
    - `suspend fun delete(uri: String)`
    - `suspend fun clear()`
    - `suspend fun setFinished(uri: String, finished: Boolean)`
    - `suspend fun restart(uri: String)` — `positionMs = 0, finished = 0`
  - `LumenDatabase.get(context): LumenDatabase` and `LumenDatabase.history(): HistoryDao`

- [ ] **Step 1: Write the DAO**

Create `app/src/main/java/com/lumen/player/library/data/HistoryDao.kt`:

```kotlin
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

    @Query("UPDATE playback_history SET lastPlayedAt = :lastPlayedAt WHERE uri = :uri")
    suspend fun touch(uri: String, lastPlayedAt: Long)

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
```

- [ ] **Step 2: Write the database**

Create `app/src/main/java/com/lumen/player/library/data/LumenDatabase.kt`:

```kotlin
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
```

- [ ] **Step 3: Generate code and schema**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:kspDebugKotlin`
Expected: BUILD SUCCESSFUL. Room's annotation processor runs; `app/schemas/com.lumen.player.library.data.LumenDatabase/1.json` is written.

Run: `ls app/schemas/com.lumen.player.library.data.LumenDatabase/` — expect `1.json`.

If KSP reports "Schema export directory was not provided", confirm the `room { schemaDirectory("$projectDir/schemas") }` block from Task 1 Step 3 is inside `android { }`.

- [ ] **Step 4: Full compile check**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/data/HistoryDao.kt \
        app/src/main/java/com/lumen/player/library/data/LumenDatabase.kt \
        app/schemas
git commit -m "feat: Room database + HistoryDao for playback history"
```

---

### Task 5: DataStore additions and legacy resume-data migration

**Files:**
- Modify: `app/src/main/java/com/lumen/player/player/PlayerPrefs.kt`
- Test: `app/src/test/java/com/lumen/player/library/HistoryMigrationTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces, on `PlayerPrefs`:
  - `val tmdbApiKey: Flow<String>`
  - `fun setTmdbApiKey(key: String)`
  - `suspend fun migrateLegacyResumeData()` — one-time; removes `pos_*` keys, sets a done flag
  - top-level `fun legacyResumeKeyNames(allKeyNames: Set<String>): Set<String>` (pure, tested)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/lumen/player/library/HistoryMigrationTest.kt`:

```kotlin
package com.lumen.player.library

import com.lumen.player.player.legacyResumeKeyNames
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMigrationTest {

    @Test fun selectsOnlyPosPrefixedKeys() {
        val all = setOf("pos_123", "pos_-456", "last_url", "tmdb_api_key", "history_migrated_v1")
        assertEquals(setOf("pos_123", "pos_-456"), legacyResumeKeyNames(all))
    }

    @Test fun emptyWhenNoLegacyKeys() {
        assertEquals(emptySet<String>(), legacyResumeKeyNames(setOf("last_url", "tmdb_api_key")))
    }

    @Test fun doesNotMatchSubstringInMiddle() {
        assertEquals(emptySet<String>(), legacyResumeKeyNames(setOf("x_pos_1", "position")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.HistoryMigrationTest"`
Expected: FAIL — unresolved reference `legacyResumeKeyNames`.

- [ ] **Step 3: Modify `PlayerPrefs.kt`**

Replace the file contents of `app/src/main/java/com/lumen/player/player/PlayerPrefs.kt` with:

```kotlin
package com.lumen.player.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("player_prefs")

private const val NEAR_EDGE_MS = 5_000L
private const val LEGACY_POSITION_PREFIX = "pos_"

/** Names of the obsolete hash-keyed resume entries in a set of DataStore key names. */
fun legacyResumeKeyNames(allKeyNames: Set<String>): Set<String> =
    allKeyNames.filterTo(mutableSetOf()) { it.startsWith(LEGACY_POSITION_PREFIX) }

/**
 * Lightweight preferences: last-played URL, TMDB API key, and a one-time migration flag.
 *
 * Per-video resume positions moved to Room ([com.lumen.player.library.HistoryRepository]) in the
 * library feature. The old `pos_<hash>` entries here cannot be mapped back to their URIs, so they
 * are cleared once on first launch of the new build rather than migrated.
 */
class PlayerPrefs private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setLastUrl(url: String) {
        scope.launch { appContext.dataStore.edit { it[LAST_URL] = url } }
    }

    val lastUrl: Flow<String> = appContext.dataStore.data.map { it[LAST_URL] ?: "" }

    val tmdbApiKey: Flow<String> = appContext.dataStore.data.map { it[TMDB_API_KEY] ?: "" }

    fun setTmdbApiKey(key: String) {
        scope.launch { appContext.dataStore.edit { it[TMDB_API_KEY] = key.trim() } }
    }

    /** Clears the obsolete `pos_*` resume entries exactly once. Safe to call on every launch. */
    suspend fun migrateLegacyResumeData() {
        val alreadyDone = appContext.dataStore.data.first()[LEGACY_MIGRATED] ?: false
        if (alreadyDone) return
        appContext.dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(LEGACY_POSITION_PREFIX) }
                .forEach { prefs.remove(it) }
            prefs[LEGACY_MIGRATED] = true
        }
    }

    companion object {
        private val LAST_URL = stringPreferencesKey("last_url")
        private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        private val LEGACY_MIGRATED = booleanPreferencesKey("history_migrated_v1")

        // Retained so a future migration could reference the old key shape.
        @Suppress("unused")
        private fun legacyPositionKey(uri: String) =
            longPreferencesKey("$LEGACY_POSITION_PREFIX${uri.hashCode()}")

        @Volatile
        private var instance: PlayerPrefs? = null

        fun get(context: Context): PlayerPrefs =
            instance ?: synchronized(this) {
                instance ?: PlayerPrefs(context.applicationContext).also { instance = it }
            }
    }
}
```

Note: `getPosition` / `savePosition` are removed. Task 8 replaces their call sites. If any other file still references them, that is expected until Task 8 — but `PlayerScreen.kt` is the only caller (verified in the spec).

- [ ] **Step 4: Run the migration test**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.HistoryMigrationTest"`
Expected: PASS (3 tests).

Do **not** run a full `compileDebugKotlin` yet — `PlayerScreen.kt` still calls the removed `PlayerPrefs.getPosition`; it is fixed in Task 8.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/player/PlayerPrefs.kt \
        app/src/test/java/com/lumen/player/library/HistoryMigrationTest.kt
git commit -m "feat: PlayerPrefs gains TMDB key + legacy resume-data cleanup"
```

---

### Task 6: `HistoryRepository`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/HistoryRepository.kt`

**Interfaces:**
- Consumes: `LumenDatabase` (Task 4), `PlaybackHistoryEntry`, `SourceType`, `normalizeMediaUri`, `isFinished`, `resumePositionForEntry` (Tasks 2–3).
- Produces `HistoryRepository`:
  - `companion object { fun get(context: Context): HistoryRepository }`
  - `suspend fun startSession(rawUri: String, sourceType: SourceType, titleHint: String, hasPersistedPermission: Boolean): Long`
  - `fun updatePosition(rawUri: String, positionMs: Long, durationMs: Long)`
  - `suspend fun updateThumbnail(rawUri: String, path: String)`
  - `suspend fun forget(rawUri: String)`
  - `suspend fun setFinished(rawUri: String, finished: Boolean)`
  - `suspend fun restart(rawUri: String)`
  - `val continueWatching: Flow<List<PlaybackHistoryEntry>>`
  - `fun recent(limit: Int): Flow<List<PlaybackHistoryEntry>>`
  - `val all: Flow<List<PlaybackHistoryEntry>>`

- [ ] **Step 1: Write the implementation**

Create `app/src/main/java/com/lumen/player/library/HistoryRepository.kt`:

```kotlin
package com.lumen.player.library

import android.content.Context
import android.util.Log
import com.lumen.player.library.data.HistoryDao
import com.lumen.player.library.data.LumenDatabase
import com.lumen.player.library.data.PlaybackHistoryEntry
import com.lumen.player.library.data.SourceType
import com.lumen.player.library.data.isFinished
import com.lumen.player.library.data.normalizeMediaUri
import com.lumen.player.library.data.resumePositionForEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val TAG = "HistoryRepository"

/** Records and exposes per-video playback history. Mirrors [com.lumen.player.player.PlayerPrefs.get]. */
class HistoryRepository private constructor(private val dao: HistoryDao) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val continueWatching: Flow<List<PlaybackHistoryEntry>> = dao.observeContinueWatching()
    val all: Flow<List<PlaybackHistoryEntry>> = dao.observeAll()
    fun recent(limit: Int): Flow<List<PlaybackHistoryEntry>> = dao.observeRecent(limit)

    /**
     * Marks the start of a playback session for [rawUri]. Creates the row if new, otherwise bumps
     * `lastPlayedAt` and keeps the stored position. Returns where playback should resume (0 for a
     * new or finished video).
     */
    suspend fun startSession(
        rawUri: String,
        sourceType: SourceType,
        titleHint: String,
        hasPersistedPermission: Boolean,
    ): Long {
        val uri = normalizeMediaUri(rawUri)
        val now = System.currentTimeMillis()
        val existing = dao.find(uri)
        if (existing == null) {
            dao.upsert(
                PlaybackHistoryEntry(
                    uri = uri,
                    sourceType = sourceType.name,
                    title = titleHint.ifBlank { uri },
                    positionMs = 0L,
                    durationMs = 0L,
                    lastPlayedAt = now,
                    finished = false,
                    thumbnailPath = null,
                    hasPersistedPermission = hasPersistedPermission,
                ),
            )
        } else {
            dao.upsert(
                existing.copy(
                    lastPlayedAt = now,
                    // Keep the best title we have; upgrade a URL-derived title if a real hint arrives.
                    title = if (titleHint.isNotBlank() && existing.title == existing.uri) {
                        titleHint
                    } else {
                        existing.title
                    },
                    hasPersistedPermission = hasPersistedPermission || existing.hasPersistedPermission,
                ),
            )
        }
        return resumePositionForEntry(dao.find(uri))
    }

    /** Fire-and-forget. Applies the finished rule. Never throws into playback. */
    fun updatePosition(rawUri: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 0L) return
        scope.launch {
            runCatching {
                val uri = normalizeMediaUri(rawUri)
                val existing = dao.find(uri) ?: return@launch
                dao.upsert(
                    existing.copy(
                        positionMs = positionMs,
                        durationMs = if (durationMs > 0L) durationMs else existing.durationMs,
                        finished = isFinished(positionMs, durationMs),
                        lastPlayedAt = System.currentTimeMillis(),
                    ),
                )
            }.onFailure { Log.w(TAG, "updatePosition failed", it) }
        }
    }

    suspend fun updateThumbnail(rawUri: String, path: String) {
        val uri = normalizeMediaUri(rawUri)
        val existing = dao.find(uri) ?: return
        dao.upsert(existing.copy(thumbnailPath = path))
    }

    suspend fun forget(rawUri: String) = dao.delete(normalizeMediaUri(rawUri))
    suspend fun setFinished(rawUri: String, finished: Boolean) =
        dao.setFinished(normalizeMediaUri(rawUri), finished)
    suspend fun restart(rawUri: String) = dao.restart(normalizeMediaUri(rawUri))

    companion object {
        @Volatile
        private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(LumenDatabase.get(context).history())
                    .also { instance = it }
            }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin`
Expected: FAIL — only in `PlayerScreen.kt` (`PlayerPrefs.getPosition` / `savePosition` unresolved). `HistoryRepository.kt` itself must compile clean; if the errors list `HistoryRepository.kt`, fix them before continuing.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/HistoryRepository.kt
git commit -m "feat: HistoryRepository over the playback-history DAO"
```

---

### Task 7: `Thumbnailer`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/Thumbnailer.kt`
- Test: `app/src/test/java/com/lumen/player/library/ThumbnailerTest.kt`

**Interfaces:**
- Consumes: `normalizeMediaUri` (Task 2).
- Produces:
  - `fun thumbFileName(rawUri: String): String` (pure, tested) — stable `.jpg` name
  - `suspend fun captureFrame(context: Context, rawUri: String, atMs: Long): String?` — writes a JPEG under `filesDir/thumbs`, returns its absolute path or null

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/lumen/player/library/ThumbnailerTest.kt`:

```kotlin
package com.lumen.player.library

import com.lumen.player.library.thumbFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailerTest {

    @Test fun sameUriGivesSameName() {
        assertEquals(
            thumbFileName("https://x.com/a.mp4"),
            thumbFileName("  https://X.com/a.mp4  "),
        )
    }

    @Test fun differentUrisDiffer() {
        assertTrue(thumbFileName("https://x.com/a.mp4") != thumbFileName("https://x.com/b.mp4"))
    }

    @Test fun endsWithJpgAndHasNoPathSeparators() {
        val name = thumbFileName("content://media/external/video/media/42")
        assertTrue(name.endsWith(".jpg"))
        assertTrue(!name.contains('/') && !name.contains('\\'))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.ThumbnailerTest"`
Expected: FAIL — unresolved reference `thumbFileName`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/lumen/player/library/Thumbnailer.kt`:

```kotlin
package com.lumen.player.library

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.lumen.player.library.data.normalizeMediaUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "Thumbnailer"
private const val THUMB_DIR = "thumbs"
private const val TARGET_WIDTH = 320
private const val JPEG_QUALITY = 80

/** Stable, path-safe file name for the cached frame of [rawUri]. */
fun thumbFileName(rawUri: String): String {
    val hash = normalizeMediaUri(rawUri).hashCode().toLong() and 0xFFFFFFFFL
    return "thumb_$hash.jpg"
}

/**
 * Grabs one representative frame near [atMs] and caches it as a JPEG under `filesDir/thumbs`.
 * Returns the absolute path, or null for sources a frame cannot be pulled from (most network
 * streams, DRM, codec failures). Never throws.
 */
suspend fun captureFrame(context: Context, rawUri: String, atMs: Long): String? =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, THUMB_DIR).apply { mkdirs() }
        val out = File(dir, thumbFileName(rawUri))
        if (out.exists() && out.length() > 0L) return@withContext out.absolutePath

        val retriever = MediaMetadataRetriever()
        try {
            val uri = rawUri.toUri()
            if (uri.scheme == "http" || uri.scheme == "https") {
                retriever.setDataSource(rawUri, HashMap<String, String>())
            } else {
                retriever.setDataSource(context, uri as Uri)
            }
            val atUs = (atMs.coerceAtLeast(0L)) * 1000L
            val frame = retriever.getScaledFrameAtTime(
                atUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                TARGET_WIDTH,
                (TARGET_WIDTH * 9 / 16),
            ) ?: return@withContext null

            FileOutputStream(out).use { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            frame.recycle()
            out.absolutePath
        } catch (t: Throwable) {
            Log.d(TAG, "no frame for $rawUri", t)
            runCatching { out.delete() }
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.ThumbnailerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/Thumbnailer.kt \
        app/src/test/java/com/lumen/player/library/ThumbnailerTest.kt
git commit -m "feat: Thumbnailer frame-grab for history cards"
```

---

### Task 8: Wire `PlayerContainer` to `HistoryRepository`

**Files:**
- Modify: `app/src/main/java/com/lumen/player/player/PlayerScreen.kt` — `PlayerScreen` signature and `PlayerContainer` (lines ~108–156 and ~211–258)

**Interfaces:**
- Consumes: `HistoryRepository` (Task 6), `Thumbnailer.captureFrame` (Task 7), `SourceType` (Task 2).
- Produces: `PlayerScreen` gains params `externalSourceType: SourceType?` and (internal) threads `SourceType` + `hasPersistedPermission` into `PlayerContainer`. `PlayerContainer` signature becomes `PlayerContainer(uri, title, sourceType, hasPersistedPermission, onBack)` — **no longer takes `prefs`**.

- [ ] **Step 1: Update imports**

In `PlayerScreen.kt`, add:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.captureFrame
import com.lumen.player.library.data.SourceType
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Replace the resume/save effects in `PlayerContainer`**

Change the `PlayerContainer` signature (currently `uri`, `title`, `prefs`, `onBack`) to:

```kotlin
@Composable
private fun PlayerContainer(
    uri: String,
    title: String,
    sourceType: SourceType,
    hasPersistedPermission: Boolean,
    onBack: () -> Unit,
) {
```

Immediately after the existing `val audioManager = ...` line, add:

```kotlin
    val history = remember { HistoryRepository.get(context) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
```

Replace this block:

```kotlin
    // Load, restoring the saved position for this URI.
    var resumedFromMs by remember(uri) { mutableLongStateOf(0L) }
    LaunchedEffect(uri) {
        val resume = prefs.getPosition(uri)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (resume > RESUME_MIN_MS) {
            player.seekTo(resume)
            resumedFromMs = resume
        }
    }

    // Persist the position periodically and when leaving.
    LaunchedEffect(uri) {
        while (true) {
            delay(POSITION_SAVE_INTERVAL_MS)
            prefs.savePosition(uri, player.currentPosition, player.duration)
        }
    }
    DisposableEffect(uri) {
        onDispose { prefs.savePosition(uri, player.currentPosition, player.duration) }
    }
```

with:

```kotlin
    // Load, restoring the saved position for this URI from the library history.
    var resumedFromMs by remember(uri) { mutableLongStateOf(0L) }
    LaunchedEffect(uri) {
        val resume = history.startSession(
            rawUri = uri,
            sourceType = sourceType,
            titleHint = title,
            hasPersistedPermission = hasPersistedPermission,
        )
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (resume > RESUME_MIN_MS) {
            player.seekTo(resume)
            resumedFromMs = resume
        }
    }

    // Persist the position periodically while playing.
    LaunchedEffect(uri) {
        while (true) {
            delay(POSITION_SAVE_INTERVAL_MS)
            history.updatePosition(uri, player.currentPosition, player.duration)
        }
    }

    // Persist on pause/stop (covers "swipe the app away") and on leaving the screen.
    DisposableEffect(uri, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                history.updatePosition(uri, player.currentPosition, player.duration)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            history.updatePosition(uri, player.currentPosition, player.duration)
        }
    }

    // Capture a poster frame once the media is ready (best-effort; silent for network streams).
    var thumbCaptured by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri, playbackState) {
        if (!thumbCaptured && playbackState == Player.STATE_READY && !uri.startsWith("http", true)) {
            thumbCaptured = true
            val at = player.currentPosition.coerceAtLeast(player.duration.coerceAtLeast(0L) / 5)
            val path = captureFrame(context, uri, at)
            if (path != null) history.updateThumbnail(uri, path)
        }
    }
```

- [ ] **Step 3: Update the `PlayerScreen` composable**

Change the `PlayerScreen` signature to add the source type:

```kotlin
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    externalUri: String? = null,
    externalSourceType: SourceType? = null,
    onExternalUriConsumed: () -> Unit = {},
) {
```

Add state next to `sourceUri` / `sourceLabel`:

```kotlin
    var sourceTypeName by rememberSaveable { mutableStateOf(SourceType.URL.name) }
    var hasPersistedPermission by rememberSaveable { mutableStateOf(true) }
```

In the external-URI `LaunchedEffect`, set the type:

```kotlin
    LaunchedEffect(externalUri) {
        if (externalUri != null) {
            if (externalUri.startsWith("http", ignoreCase = true)) prefs.setLastUrl(externalUri)
            sourceUri = externalUri
            sourceLabel = externalUri.toUri().lastPathSegment ?: "Now playing"
            sourceTypeName = (externalSourceType ?: SourceType.EXTERNAL_VIEW).name
            hasPersistedPermission = externalSourceType == null // URLs: true; content:// set by caller in Task 9
            onExternalUriConsumed()
        }
    }
```

> Note: Task 9 refines `hasPersistedPermission` for the external `content://` case. For now, `true` for `http(s)` and `false` for a bare external file is a safe default.

Update the `PlayerContainer` call site (the `else` branch):

```kotlin
            PlayerContainer(
                uri = uri,
                title = sourceLabel.ifBlank { "Now playing" },
                sourceType = runCatching { SourceType.valueOf(sourceTypeName) }.getOrDefault(SourceType.URL),
                hasPersistedPermission = hasPersistedPermission,
                onBack = { sourceUri = null },
            )
```

In the `SourcePicker` `onPlay` callback (still present until Task 11), set:

```kotlin
                onPlay = { value, label ->
                    if (value.startsWith("http", ignoreCase = true)) prefs.setLastUrl(value)
                    sourceUri = value
                    sourceLabel = label
                    sourceTypeName = if (value.startsWith("http", true)) {
                        SourceType.URL.name
                    } else {
                        SourceType.SAF_FILE.name
                    }
                    hasPersistedPermission = true
                },
```

And in `SourcePicker`'s `OpenDocument` result, take a persistable grant (the picker currently just calls `onPlay`):

```kotlin
    val context = LocalContext.current
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPlay(picked.toString(), picked.lastPathSegment ?: "Local file")
        }
    }
```

- [ ] **Step 4: Compile check**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin`
Expected: FAIL — `MainActivity.kt` does not yet pass `externalSourceType` (that is Task 9). No errors should be reported inside `PlayerScreen.kt`; if there are, fix them.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/player/PlayerScreen.kt
git commit -m "feat: player records playback into HistoryRepository"
```

---

### Task 9: `MainActivity` source type + persistable permission

**Files:**
- Modify: `app/src/main/java/com/lumen/player/MainActivity.kt`

**Interfaces:**
- Consumes: `SourceType` (Task 2), `PlayerScreen(externalUri, externalSourceType, onExternalUriConsumed)` (Task 8).
- Produces: `MainActivity` resolves a `SourceType` per intent and, for `content://`, attempts `takePersistableUriPermission`; passes the type to `PlayerScreen`.

- [ ] **Step 1: Rewrite `MainActivity.kt`**

```kotlin
package com.lumen.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.lumen.player.library.data.SourceType
import com.lumen.player.player.PlayerScreen
import com.lumen.player.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {

    private data class IncomingVideo(val uri: Uri, val sourceType: SourceType)

    private var incoming by mutableStateOf<IncomingVideo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        incoming = extractVideo(intent)
        setContent {
            LumenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerScreen(
                        externalUri = incoming?.uri?.toString(),
                        externalSourceType = incoming?.sourceType,
                        onExternalUriConsumed = { incoming = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractVideo(intent)?.let { incoming = it }
    }

    private fun extractVideo(intent: Intent?): IncomingVideo? {
        val (uri, type) = when (intent?.action) {
            Intent.ACTION_VIEW ->
                intent.data to SourceType.EXTERNAL_VIEW
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) to
                    SourceType.EXTERNAL_SEND
            else -> null to SourceType.EXTERNAL_VIEW
        }
        if (uri == null) return null
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        return IncomingVideo(uri, type)
    }
}
```

> `takePersistableUriPermission` throws `SecurityException` when the sender did not add `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` — hence the `runCatching`. Whether the grant stuck is re-checked in Task 10 via `contentResolver.persistedUriPermissions`, but for Phase 1 the player simply records the row and the "may be dead" affordance (Task 11) covers the failure.

- [ ] **Step 2: Refine `hasPersistedPermission` in `PlayerScreen`**

In `PlayerScreen.kt`, replace the placeholder line from Task 8 Step 3:

```kotlin
            hasPersistedPermission = externalSourceType == null
```

with:

```kotlin
            hasPersistedPermission = when {
                externalUri.startsWith("http", ignoreCase = true) -> true
                else -> context.contentResolver.persistedUriPermissions.any {
                    it.uri.toString() == externalUri && it.isReadPermission
                }
            }
```

(`context` is already in scope in `PlayerScreen` as `val context = LocalContext.current`.)

- [ ] **Step 3: Full compile + unit tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. All unit tests pass (existing 23 + new: 8 + 7 + 3 + 3 = 44).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lumen/player/MainActivity.kt \
        app/src/main/java/com/lumen/player/player/PlayerScreen.kt
git commit -m "feat: MainActivity resolves SourceType + persists content:// grants"
```

---

### Task 10: `rememberBitmap` and history card composables

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/HistoryCards.kt`

**Interfaces:**
- Consumes: `PlaybackHistoryEntry` (Task 3).
- Produces:
  - `@Composable fun rememberBitmap(path: String?): ImageBitmap?`
  - `@Composable fun ContinueWatchingCard(entry: PlaybackHistoryEntry, onClick: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun HistoryRow(entry: PlaybackHistoryEntry, onClick: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun HistoryItemMenu(entry: PlaybackHistoryEntry, onDismiss: () -> Unit, onRestart: () -> Unit, onMarkFinished: () -> Unit, onRemove: () -> Unit)`
  - `fun remainingLabel(positionMs: Long, durationMs: Long): String` (pure)

- [ ] **Step 1: Write the failing test for the label helper**

Create `app/src/test/java/com/lumen/player/library/RemainingLabelTest.kt`:

```kotlin
package com.lumen.player.library

import com.lumen.player.library.ui.remainingLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingLabelTest {

    @Test fun showsPercentWhenDurationKnown() {
        assertEquals("45% watched", remainingLabel(positionMs = 810_000L, durationMs = 1_800_000L))
    }

    @Test fun fallsBackToElapsedWhenDurationUnknown() {
        assertEquals("13:30 in", remainingLabel(positionMs = 810_000L, durationMs = 0L))
    }

    @Test fun clampsPercentToRange() {
        assertEquals("99% watched", remainingLabel(positionMs = 1_799_999L, durationMs = 1_800_000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.RemainingLabelTest"`
Expected: FAIL — unresolved reference.

- [ ] **Step 3: Write `HistoryCards.kt`**

Create `app/src/main/java/com/lumen/player/library/ui/HistoryCards.kt`:

```kotlin
package com.lumen.player.library.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.lumen.player.library.data.PlaybackHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Accent = Color(0xFF4C8DFF)
private val Surface = Color(0xFF16161A)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

private val bitmapCache = LruCache<String, ImageBitmap>(24)

@Composable
fun rememberBitmap(path: String?): ImageBitmap? {
    val value by produceState<ImageBitmap?>(initialValue = path?.let { bitmapCache.get(it) }, path) {
        if (path == null) { value = null; return@produceState }
        bitmapCache.get(path)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                .getOrNull()
                ?.also { bitmapCache.put(path, it) }
        }
    }
    return value
}

/** "45% watched" when the duration is known, otherwise "13:30 in". */
fun remainingLabel(positionMs: Long, durationMs: Long): String {
    if (durationMs > 0L) {
        val pct = ((positionMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 99)
        return "$pct% watched"
    }
    val totalSec = positionMs / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d in".format(m, s)
}

@Composable
private fun Poster(path: String?, modifier: Modifier = Modifier) {
    val bmp = rememberBitmap(path)
    Box(modifier = modifier.background(Surface), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = TextSecondary,
                modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    entry: PlaybackHistoryEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(bottom = 4.dp),
    ) {
        Box {
            Poster(
                entry.thumbnailPath,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            val fraction = if (entry.durationMs > 0L) {
                (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { fraction },
                color = Accent,
                trackColor = Color(0x33FFFFFF),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
        Text(entry.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(remainingLabel(entry.positionMs, entry.durationMs), color = TextSecondary, fontSize = 11.sp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    entry: PlaybackHistoryEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Poster(entry.thumbnailPath, Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(6.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.finished) "Finished" else remainingLabel(entry.positionMs, entry.durationMs),
                color = TextSecondary, fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun HistoryItemMenu(
    entry: PlaybackHistoryEntry,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    onMarkFinished: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Restart from beginning") },
            onClick = { onRestart(); onDismiss() })
        if (!entry.finished) {
            DropdownMenuItem(text = { Text("Mark finished") },
                onClick = { onMarkFinished(); onDismiss() })
        }
        DropdownMenuItem(text = { Text("Remove") }, onClick = { onRemove(); onDismiss() })
    }
}
```

> If `import androidx.compose.foundation.clip` fails to resolve, remove that line — the correct import `androidx.compose.ui.draw.clip` is already present. (Listed twice deliberately so the executor keeps the `ui.draw` one.)

- [ ] **Step 4: Run tests + compile**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest --tests "com.lumen.player.library.RemainingLabelTest" && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin`
Expected: tests PASS (3); compile BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/HistoryCards.kt \
        app/src/test/java/com/lumen/player/library/RemainingLabelTest.kt
git commit -m "feat: history card composables + bitmap loader"
```

---

### Task 11: `LibraryScreen` replaces `SourcePicker`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/LibraryScreen.kt`
- Modify: `app/src/main/java/com/lumen/player/player/PlayerScreen.kt` — home routing; remove `SourcePicker`

**Interfaces:**
- Consumes: `HistoryRepository` (Task 6), `ContinueWatchingCard` / `HistoryRow` / `HistoryItemMenu` (Task 10), `SourceType` (Task 2).
- Produces:
  - `@Composable fun LibraryScreen(lastUrl: String, onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier)`
  - `enum class HomeRoute { Library, History, Settings }` in `PlayerScreen.kt`

- [ ] **Step 1: Write `LibraryScreen.kt`**

Create `app/src/main/java/com/lumen/player/library/ui/LibraryScreen.kt`:

```kotlin
package com.lumen.player.library.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.data.SourceType
import com.lumen.player.library.data.qualifiesForContinueWatching
import kotlinx.coroutines.launch

private val Background = Color(0xFF0B0B0D)
private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

@Composable
fun LibraryScreen(
    lastUrl: String,
    onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val history = remember { HistoryRepository.get(context) }
    val scope = rememberCoroutineScope()

    val continueWatching by history.continueWatching.collectAsState(emptyList())
    val recent by history.recent(24).collectAsState(emptyList())
    // "Recent" = rows not already shown in Continue Watching.
    val continueUris = continueWatching.map { it.uri }.toSet()
    val recentOnly = recent.filter { it.uri !in continueUris }.take(10)

    var url by remember { mutableStateOf("") }
    if (url.isEmpty() && lastUrl.isNotEmpty()) url = lastUrl

    var menuUri by remember { mutableStateOf<String?>(null) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPlay(picked.toString(), picked.lastPathSegment ?: "Local file", SourceType.SAF_FILE, true)
        }
    }

    fun play(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        onPlay(trimmed, trimmed, SourceType.URL, true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lumen", color = TextPrimary, fontSize = 26.sp,
                        modifier = Modifier.weight(1f))
                    var overflow by remember { mutableStateOf(false) }
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimary)
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(text = { Text("History") },
                            onClick = { overflow = false; onOpenHistory() })
                        DropdownMenuItem(text = { Text("Settings") },
                            onClick = { overflow = false; onOpenSettings() })
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("Video URL") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { play(url) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { play(url) }) { Text("Play") }
                        Button(onClick = { openDocument.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Text("  Open file")
                        }
                    }
                }
            }

            if (continueWatching.isNotEmpty()) {
                item {
                    Text("Continue watching", color = TextPrimary, fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { it.uri }) { entry ->
                            Box {
                                ContinueWatchingCard(
                                    entry = entry,
                                    onClick = {
                                        onPlay(
                                            entry.uri, entry.title,
                                            runCatching { SourceType.valueOf(entry.sourceType) }
                                                .getOrDefault(SourceType.URL),
                                            entry.hasPersistedPermission,
                                        )
                                    },
                                    onLongPress = { menuUri = entry.uri },
                                )
                                if (menuUri == entry.uri) {
                                    HistoryItemMenu(
                                        entry = entry,
                                        onDismiss = { menuUri = null },
                                        onRestart = { scope.launch { history.restart(entry.uri) } },
                                        onMarkFinished = {
                                            scope.launch { history.setFinished(entry.uri, true) }
                                        },
                                        onRemove = { scope.launch { history.forget(entry.uri) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (recentOnly.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent", color = TextPrimary, fontSize = 15.sp,
                            modifier = Modifier.weight(1f))
                        Text("See all", color = Color(0xFF4C8DFF), fontSize = 13.sp,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickableNoRipple(onOpenHistory))
                    }
                }
                items(recentOnly, key = { it.uri }) { entry ->
                    Box {
                        HistoryRow(
                            entry = entry,
                            onClick = {
                                onPlay(
                                    entry.uri, entry.title,
                                    runCatching { SourceType.valueOf(entry.sourceType) }
                                        .getOrDefault(SourceType.URL),
                                    entry.hasPersistedPermission,
                                )
                            },
                            onLongPress = { menuUri = entry.uri },
                        )
                        if (menuUri == entry.uri) {
                            HistoryItemMenu(
                                entry = entry,
                                onDismiss = { menuUri = null },
                                onRestart = { scope.launch { history.restart(entry.uri) } },
                                onMarkFinished = {
                                    scope.launch { history.setFinished(entry.uri, true) }
                                },
                                onRemove = { scope.launch { history.forget(entry.uri) } },
                            )
                        }
                    }
                }
            }

            if (continueWatching.isEmpty() && recentOnly.isEmpty()) {
                item {
                    Text(
                        "Videos you play show up here.",
                        color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(
        androidx.compose.foundation.clickable(
            interactionSource = interaction, indication = null, onClick = onClick,
        ),
    )
}
```

- [ ] **Step 2: Route the home screen in `PlayerScreen.kt`**

Add near the top of `PlayerScreen.kt`:

```kotlin
enum class HomeRoute { Library, History, Settings }
```

Add imports:

```kotlin
import com.lumen.player.library.ui.LibraryScreen
import com.lumen.player.library.ui.HistoryScreen
import com.lumen.player.library.ui.SettingsScreen
```

In `PlayerScreen`, add state:

```kotlin
    var homeRoute by rememberSaveable { mutableStateOf(HomeRoute.Library) }
```

Replace the `if (uri == null) { SourcePicker(...) }` branch with:

```kotlin
        if (uri == null) {
            BackHandler(enabled = homeRoute != HomeRoute.Library) { homeRoute = HomeRoute.Library }
            when (homeRoute) {
                HomeRoute.Library -> LibraryScreen(
                    lastUrl = lastUrl,
                    onPlay = { value, label, type, hasPerm ->
                        if (value.startsWith("http", ignoreCase = true)) prefs.setLastUrl(value)
                        sourceUri = value
                        sourceLabel = label
                        sourceTypeName = type.name
                        hasPersistedPermission = hasPerm
                    },
                    onOpenHistory = { homeRoute = HomeRoute.History },
                    onOpenSettings = { homeRoute = HomeRoute.Settings },
                    modifier = Modifier.safeDrawingPadding(),
                )
                HomeRoute.History -> HistoryScreen(
                    onPlay = { value, label, type, hasPerm ->
                        sourceUri = value
                        sourceLabel = label
                        sourceTypeName = type.name
                        hasPersistedPermission = hasPerm
                    },
                    onBack = { homeRoute = HomeRoute.Library },
                    modifier = Modifier.safeDrawingPadding(),
                )
                HomeRoute.Settings -> SettingsScreen(
                    onBack = { homeRoute = HomeRoute.Library },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        } else {
```

Delete the entire `@Composable private fun SourcePicker(...)` function (lines ~158–209) — it is fully replaced. Remove now-unused imports it owned only if the compiler flags them (`Button`, `OutlinedButton`, `OutlinedTextField`, `KeyboardActions`, `KeyboardOptions`, `ImeAction`, `Arrangement` may still be used elsewhere — let the compiler guide removal).

- [ ] **Step 3: Compile check**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin`
Expected: FAIL only for missing `HistoryScreen` / `SettingsScreen` (Task 12). No errors inside `LibraryScreen.kt` or the `PlayerScreen.kt` routing block.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/LibraryScreen.kt \
        app/src/main/java/com/lumen/player/player/PlayerScreen.kt
git commit -m "feat: LibraryScreen home with Continue Watching + Recent"
```

---

### Task 12: `HistoryScreen` and `SettingsScreen`

**Files:**
- Create: `app/src/main/java/com/lumen/player/library/ui/HistoryScreen.kt`
- Create: `app/src/main/java/com/lumen/player/library/ui/SettingsScreen.kt`

**Interfaces:**
- Consumes: `HistoryRepository` (Task 6), `HistoryRow` / `HistoryItemMenu` (Task 10), `PlayerPrefs` (Task 5), `SourceType`.
- Produces:
  - `@Composable fun HistoryScreen(onPlay: (String, String, SourceType, Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write `HistoryScreen.kt`**

```kotlin
package com.lumen.player.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.library.HistoryRepository
import com.lumen.player.library.data.SourceType
import kotlinx.coroutines.launch

private val TextPrimary = Color(0xFFF4F4F5)

@Composable
fun HistoryScreen(
    onPlay: (String, String, SourceType, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val history = remember { HistoryRepository.get(context) }
    val scope = rememberCoroutineScope()
    val all by history.all.collectAsState(emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    var menuUri by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = TextPrimary)
                    }
                    Text("History", color = TextPrimary, fontSize = 18.sp,
                        modifier = Modifier.weight(1f))
                    if (all.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
                    }
                }
            }
            items(all, key = { it.uri }) { entry ->
                Box {
                    HistoryRow(
                        entry = entry,
                        onClick = {
                            onPlay(
                                entry.uri, entry.title,
                                runCatching { SourceType.valueOf(entry.sourceType) }
                                    .getOrDefault(SourceType.URL),
                                entry.hasPersistedPermission,
                            )
                        },
                        onLongPress = { menuUri = entry.uri },
                    )
                    if (menuUri == entry.uri) {
                        HistoryItemMenu(
                            entry = entry,
                            onDismiss = { menuUri = null },
                            onRestart = { scope.launch { history.restart(entry.uri) } },
                            onMarkFinished = { scope.launch { history.setFinished(entry.uri, true) } },
                            onRemove = { scope.launch { history.forget(entry.uri) } },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("Resume positions for every video will be forgotten.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        history.all.value.forEach { history.forget(it.uri) }
                    }
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
```

> `history.all` is a `Flow`, not a `StateFlow` — `.value` is not available. Replace the confirm action body with a dedicated repository call: add `suspend fun clearAll() = dao.clear()` to `HistoryRepository` (Task 6 file) and call `scope.launch { history.clearAll() }` here. Make that one-line addition to `HistoryRepository` now and amend the Task 6 commit is not needed — just include it in this task's commit.

- [ ] **Step 2: Add `clearAll` to `HistoryRepository`**

In `app/src/main/java/com/lumen/player/library/HistoryRepository.kt`, add next to `forget`:

```kotlin
    suspend fun clearAll() = dao.clear()
```

Change the `HistoryScreen` confirm button body to:

```kotlin
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { history.clearAll() }
                }) { Text("Clear all") }
```

- [ ] **Step 3: Write `SettingsScreen.kt`**

```kotlin
package com.lumen.player.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.player.player.PlayerPrefs

private val TextPrimary = Color(0xFFF4F4F5)
private val TextSecondary = Color(0xFFA1A1AA)

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val prefs = remember { PlayerPrefs.get(context) }
    val savedKey by prefs.tmdbApiKey.collectAsState("")
    var key by remember(savedKey) { mutableStateOf(savedKey) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Settings", color = TextPrimary, fontSize = 18.sp)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("TMDB API key", color = TextPrimary, fontSize = 15.sp)
            Text(
                "Used to fetch posters and titles for library videos (added in a later update). " +
                    "Create a free key at themoviedb.org → Settings → API.",
                color = TextSecondary, fontSize = 12.sp,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                singleLine = true,
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { prefs.setTmdbApiKey(key) }) { Text("Save") }
        }
    }
}
```

- [ ] **Step 4: Full build + tests + lint**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL. 44 unit tests pass. Lint clean (the project's `lint.abortOnError = true`).

If lint reports `newApi` for `IntentCompat` or `getScaledFrameAtTime` — it should not (minSdk 36) — but if so, address per the message.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lumen/player/library/ui/HistoryScreen.kt \
        app/src/main/java/com/lumen/player/library/ui/SettingsScreen.kt \
        app/src/main/java/com/lumen/player/library/HistoryRepository.kt
git commit -m "feat: History and Settings screens"
```

---

### Task 13: Migration hook, README, and full verification

**Files:**
- Modify: `app/src/main/java/com/lumen/player/player/PlayerScreen.kt` — call `migrateLegacyResumeData()` once
- Modify: `README.md`

**Interfaces:**
- Consumes: `PlayerPrefs.migrateLegacyResumeData` (Task 5).
- Produces: nothing new.

- [ ] **Step 1: Run the one-time migration on first composition**

In `PlayerScreen.kt`, in the `PlayerScreen` composable, next to the existing `LaunchedEffect(Unit) { updateController.checkOnce() }`:

```kotlin
    LaunchedEffect(Unit) { prefs.migrateLegacyResumeData() }
```

- [ ] **Step 2: Update README features table**

In `README.md`, in the Features table, replace the `Resume` row with:

```markdown
| Library | Home screen "Continue watching" + full history (Room); every play — URL, in-app file, or "Open with" from another app — is recorded and resumable |
| Resume | per-video position; "Resumed from …" chip; survives app-swipe (written on pause/stop) |
```

Add to the same table:

```markdown
| Settings | TMDB API key field (metadata matching lands in a later update) |
```

- [ ] **Step 3: Full clean verification**

Run:
```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew clean :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --stacktrace
```
Expected: BUILD SUCCESSFUL. Release APK builds (R8 + shrink) — confirms no missing ProGuard keep rules for Room (Room generates code that R8 handles without extra rules; if `assembleRelease` fails with a Room-related `ClassNotFoundException` at build time, add to `app/proguard-rules.pro`: `-keep class * extends androidx.room.RoomDatabase { <init>(); }` and `-dontwarn androidx.room.paging.**`).

- [ ] **Step 4: Manual QA on a device/emulator**

Install: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:installDebug`

Verify:
- [ ] Fresh state: home shows header + play bar + "Videos you play show up here."
- [ ] Play a URL; let it run past 0:10; press Back; home now shows a Continue watching card; tap it → resumes near where you left off; "Resumed from …" chip appears.
- [ ] While playing, swipe the app away from Recents; reopen; the card position advanced to roughly where you swiped.
- [ ] "Open file" → pick a local video → play → Back → card present → tap → resumes.
- [ ] Open a local video from a file-manager app ("Open with" → Lumen); stop midway; relaunch Lumen from its icon → card present in Continue watching; tap → resumes (when the file manager granted persistable permission; otherwise playback may error and the row stays for manual removal).
- [ ] Long-press a card → Restart / Mark finished / Remove all work.
- [ ] Play a video to its end → it leaves Continue watching, appears under History as "Finished".
- [ ] Overflow → History → list present, swipe/remove + Clear all work. Overflow → Settings → type a key → Save → reopen Settings → key persisted.

- [ ] **Step 5: Commit and open the PR**

```bash
git add app/src/main/java/com/lumen/player/player/PlayerScreen.kt README.md
git commit -m "feat: run legacy resume-data migration; document the library"
git push -u origin feat/local-media-library
gh pr create --title "Local media library — Phase 1: Continue Watching + universal resume" \
  --body "$(cat <<'EOF'
Implements Phase 1 of docs/superpowers/specs/2026-08-31-local-media-library-design.md.

- Room store (`playback_history`), `HistoryRepository`
- Every play (URL / SAF file / external VIEW/SEND) recorded with resume position
- Position written every 5s and on pause/stop (survives app-swipe)
- New `LibraryScreen` home: Continue Watching rail + Recent list + compact play bar
- `HistoryScreen`, `SettingsScreen` (TMDB key field, unwired until Phase 2)
- `content://` grants persisted via `takePersistableUriPermission` where the sender allows
- Legacy hash-keyed DataStore resume entries cleared once (not migratable)

Phase 2 (folders + SAF scan + TMDB matching) is a separate plan.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-review

**Spec coverage:**

| Spec section | Task(s) |
|---|---|
| Build changes (KSP + Room) | 1 |
| `PlaybackHistoryEntry` schema | 3 |
| `HistoryDao` queries | 4 |
| `normalizeMediaUri` | 2 |
| `finished` rule | 3 |
| DataStore → Room migration (clear `pos_*`, `history_migrated_v1`, keep `last_url`, add `tmdb_api_key`) | 5, 13 |
| `HistoryRepository` (`startSession`/`updatePosition`/`forget`/`setFinished`/`restart`) | 6 (+ `clearAll` in 12) |
| `PlayerContainer` swap + 5s/lifecycle/dispose writes + thumbnail grab | 8 |
| `MainActivity` sourceType + `takePersistableUriPermission` | 9 |
| In-app "Open file" → `ACTION_OPEN_DOCUMENT` + persistable grant | 8 (SourcePicker interim), 11 (LibraryScreen) |
| Error case: dead `content://` → dialog / row stays | 9 note + 11 (`hasPersistedPermission` carried on cards; ErrorOverlay covers playback failure). **Partial**: an explicit pre-play warning dialog for `hasPersistedPermission == false` content URIs is **not** built in this plan — the row simply carries the flag and a failed open surfaces the existing `ErrorOverlay`. Acceptable per spec's "no auto-delete"; a dedicated dialog can be a fast follow. |
| `LibraryScreen` (header, play bar, Continue Watching, Recent) | 11 |
| `HistoryScreen` | 12 |
| `SettingsScreen` (TMDB key field) | 12 |
| Lightweight `homeRoute` nav, BackHandler to Library | 11 |
| Thumbnails via `filesDir/thumbs` + `rememberBitmap` (no Coil) | 7, 10 |
| Empty/first-run state | 11 |
| Tests: `NormalizeMediaUriTest`, `FinishedRuleTest`, `DataStoreMigrationTest` | 2, 3, 5 (as `MediaUriTest`, `PlaybackHistoryRulesTest`, `HistoryMigrationTest`) |
| Tests: `HistoryDaoTest` | **Deferred** — spec's stated fallback ("keep query predicates in a pure helper and unit-test that") is taken: `qualifiesForContinueWatching` is unit-tested (Task 3) and the DAO query mirrors it (comment in `HistoryDao`). No Robolectric added. DAO correctness is covered by Task 13 manual QA. |

**Placeholder scan:** No "TBD"/"handle edge cases"/"similar to Task N". Two deliberate inline notes flag a duplicated import line (Task 10) and a `Flow` vs `StateFlow` fix (Task 12) — both give the exact resolution.

**Type consistency:**
- `PlayerContainer(uri, title, sourceType, hasPersistedPermission, onBack)` — defined Task 8, called Task 8/11. `prefs` param removed consistently.
- `onPlay` lambda shape `(rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean)` — Task 11 (`LibraryScreen`), Task 12 (`HistoryScreen` uses the 4-arg form `(String, String, SourceType, Boolean)`), consumed in `PlayerScreen` routing (Task 11 Step 2).
- `HistoryRepository` methods take `rawUri` and normalize internally — call sites pass `entry.uri` (already normalized, `normalizeMediaUri` is idempotent — Task 2 test) or raw play-bar text.
- `HomeRoute` enum — defined and used in `PlayerScreen.kt` (Task 11).
- `remainingLabel` / `thumbFileName` / `legacyResumeKeyNames` / `resumePositionForEntry` — pure, each defined once and tested once.
