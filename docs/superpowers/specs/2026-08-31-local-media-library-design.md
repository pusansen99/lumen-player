# Local Media Library + Universal Resume — Design

Date: 2026-08-31
Status: approved for implementation (Phase 1 first)
Branch: `feat/local-media-library`

## Problem

Lumen is a "paste a URL / open a file" player. Playback position is
saved (`PlayerPrefs.savePosition`, DataStore, keyed by `uri.hashCode()`)
but it is invisible: there is no list, so the only way to resume is to
re-open the exact same URI from outside the app. Files opened from a
file manager (`content://`) frequently cannot be re-opened at all
because the grant was not persistable.

Goal: every play — typed URL, in-app file pick, or external
`ACTION_VIEW` / `ACTION_SEND` — is remembered with its position and
surfaced in a **Continue Watching** list on the home screen. Tapping a
row resumes. On top of that, a browsable on-device library (user-added
SAF folders) with optional TMDB metadata.

## Decisions (locked)

| Question | Decision |
|---|---|
| Persistence | Room (SQLite) + KSP. DataStore stays for prefs only. |
| TMDB key | User enters their own key in Settings; stored locally. No key shipped. |
| Folder access | SAF `ACTION_OPEN_DOCUMENT_TREE`, persistable permission. No `READ_MEDIA_VIDEO`, no MediaStore. |
| Sequencing | Approach B: Phase 1 = history/resume + Room + playback wiring. Phase 2 = folders + scan + TMDB. Same end state. |

## Approach B — phasing

- **Phase 1** — Room, `playback_history` entity, `HistoryRepository`,
  `LibraryScreen` home restructure with Continue Watching + Recent,
  playback integration, persistable-permission fixes, thumbnail frame
  grab, History + Settings screens (Settings has the TMDB key field
  only, unwired). Ships the "resume everywhere" outcome.
- **Phase 2** — `LibraryFolder` / `LibraryVideo` / `TmdbMetadata` /
  `TmdbMatchCache` entities, `FolderScanner`, `tmdb/` package, folder
  UI (poster grid, show grouping), detail sheet, Settings "Verify"
  wired. Risky filename-parse / TMDB-match work isolated here.

Each phase is its own implementation plan and its own PR chain.
Phase 2 is re-detailed at its own planning time; the schema and
package layout below are the intended shape, not frozen.

---

## Build changes (Phase 1)

- Version catalog: add `ksp` plugin (`com.google.devtools.ksp`,
  version aligned to Kotlin 2.4.10), `androidx-room-runtime`,
  `androidx-room-ktx`, `androidx-room-compiler`.
- `app/build.gradle.kts`: apply `alias(libs.plugins.ksp)`; deps
  `implementation(libs.androidx.room.runtime)`,
  `implementation(libs.androidx.room.ktx)`,
  `ksp(libs.androidx.room.compiler)`. Room schema export dir
  `app/schemas` (checked in), added to `sourceSets` test assets for
  migration tests later.
- minSdk unchanged (Room supports 21+).
- ProGuard: Room generates code, no keep rules normally needed; verify
  release smoke build.

---

## Phase 1 — data model

### `LumenDatabase` (Room, version 1)

Single entity in Phase 1. `@Database(entities = [PlaybackHistoryEntry::class], version = 1, exportSchema = true)`.
Singleton built like `PlayerPrefs.get(context)` — `LumenDatabase.get(context)`.

```kotlin
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntry(
    @PrimaryKey val uri: String,        // normalized; see normalizeMediaUri
    val sourceType: String,             // URL | SAF_FILE | EXTERNAL_VIEW | EXTERNAL_SEND
    val title: String,                  // filename / URL host / (Phase 2) TMDB title
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long,             // epoch ms, sort key
    val finished: Boolean,
    val thumbnailPath: String?,         // filesDir/thumbs/<hash>.jpg; null if grab failed
    val hasPersistedPermission: Boolean,// content:// only; false => may be dead
    val metadataId: Long? = null        // Phase 2 FK
)
```

`sourceType` stored as `String` (not a Room enum converter) to keep
migrations trivial. A `SourceType` enum wraps it in Kotlin.

### `HistoryDao`

```kotlin
@Upsert suspend fun upsert(entry: PlaybackHistoryEntry)
@Query("SELECT positionMs FROM playback_history WHERE uri = :uri")
suspend fun positionOf(uri: String): Long?
@Query("SELECT * FROM playback_history WHERE uri = :uri")
suspend fun find(uri: String): PlaybackHistoryEntry?
@Query("SELECT * FROM playback_history WHERE finished = 0 AND positionMs > 5000 ORDER BY lastPlayedAt DESC LIMIT 30")
fun observeContinueWatching(): Flow<List<PlaybackHistoryEntry>>
@Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
fun observeAll(): Flow<List<PlaybackHistoryEntry>>
@Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :n")
fun observeRecent(n: Int): Flow<List<PlaybackHistoryEntry>>
@Query("DELETE FROM playback_history WHERE uri = :uri") suspend fun delete(uri: String)
@Query("DELETE FROM playback_history") suspend fun clear()
```

### URI normalization

`fun normalizeMediaUri(raw: String): String` in `library/data/`:
- trim whitespace
- `http`/`https`: drop URL fragment (`#…`); lowercase scheme + host;
  keep path, query, case of path
- `content://`, `file://`: return verbatim (trimmed)
- anything else: verbatim

This is the resume key. It must be stable across sessions and across
the URL-picker vs external-intent entry paths. Unit-tested with a
table of cases.

### `finished` rule

`finished = durationMs > 0 && positionMs > durationMs - 5000`
(matches current `NEAR_EDGE_MS`). Near-start (`positionMs < 5000`)
stays in the table (so it shows in History / Recent) but is excluded
from Continue Watching by the DAO query. Re-opening a `finished` row
seeks to 0 and clears the flag on the next position write.

### Migration from DataStore

Old resume data is `pos_<uri.hashCode()>` in `player_prefs` — the
original URI is not recoverable from a hash. Therefore **no data
migration**: on first run of the new build, a one-time pass removes
all `player_prefs` keys starting with `pos_`. Guard with a boolean
pref `history_migrated_v1`. `last_url` is untouched. Add DataStore
string `tmdb_api_key` (empty default).

---

## Phase 1 — playback integration

### `HistoryRepository`

Singleton (`HistoryRepository.get(context)`), wraps `HistoryDao`.

```kotlin
// Called on entering PlayerContainer. Upserts (new row, or bump
// lastPlayedAt + keep positionMs), returns resume position (0 if new
// or finished).
suspend fun startSession(
    uri: String, sourceType: SourceType,
    titleHint: String, hasPersistedPermission: Boolean
): Long

// Fire-and-forget IO. Applies the finished rule. No-op writes that
// would only churn lastPlayedAt are fine.
fun updatePosition(uri: String, positionMs: Long, durationMs: Long)

suspend fun forget(uri: String)
suspend fun setFinished(uri: String, finished: Boolean)
suspend fun restart(uri: String)   // positionMs = 0, finished = false
```

All writes on `Dispatchers.IO`; failures logged, never thrown into
playback.

### `PlayerContainer` changes (`PlayerScreen.kt`)

- Drop `PlayerPrefs.getPosition` / `savePosition`; use
  `HistoryRepository`.
- `LaunchedEffect(uri)`: `resumeMs = repo.startSession(normalized, sourceType, titleHint, hasPerm)`;
  seek to `resumeMs` when `> 0`; existing "Resumed from …" chip
  unchanged.
- Position writes — currently only on dispose. Add:
  - a `LaunchedEffect` that, while `isPlaying`, writes every 5 s;
  - a write on `Lifecycle.Event.ON_PAUSE` and `ON_STOP`
    (`LifecycleEventObserver`);
  - the existing `DisposableEffect { onDispose { … } }` write.
  This makes "swipe the app away mid-video" resume reliably.
- Thumbnail (Phase 1): on first `STATE_READY`, one IO job —
  `MediaMetadataRetriever` (or `retriever.getScaledFrameAtTime` where
  available) at `max(resumeMs, durationMs/5)`, scaled to ~320 px wide,
  JPEG 80, written to `filesDir/thumbs/<uri hash>.jpg`; store path.
  HLS/DASH/live/most `http` → grab fails → caught, `thumbnailPath`
  stays null. Never retried in the same session.

### `MainActivity` changes

- Derive `sourceType`: `Intent.ACTION_VIEW` → `EXTERNAL_VIEW`,
  `ACTION_SEND` → `EXTERNAL_SEND`. Thread it through `externalUri`
  into `PlayerScreen` alongside the URI.
- For a `content://` VIEW/SEND URI:
  `runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }`.
  Succeeds only if the grantor set
  `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` (many file managers do not).
  Success/failure → `hasPersistedPermission`.

### In-app "Open file"

Switch the picker from `ACTION_GET_CONTENT` to `ACTION_OPEN_DOCUMENT`
(`ActivityResultContracts.OpenDocument`), then
`takePersistableUriPermission`. `sourceType = SAF_FILE`. These become
reliably resumable.

### Error handling

| Case | Behavior |
|---|---|
| Continue Watching tap → open throws (`SecurityException`, `FileNotFoundException`, dead `content://`) | Dialog: "This video can't be opened. Reopen it from your file manager." → [Remove from list] / [Cancel]. Set `hasPersistedPermission = false`. No auto-delete. |
| URL row 404s later | Existing `ErrorOverlay` with Retry. Row stays. |
| Thumbnail grab OOM / codec failure | Caught; no thumbnail; title-initials placeholder. |
| DB write failure | Fire-and-forget, logged, playback unaffected. |

---

## Phase 1 — UI

`LibraryScreen` replaces `SourcePicker` as the home destination.
`PlayerScreen`: `selectedUri == null` → home, else `PlayerContainer`
(unchanged). Lightweight nav — a `homeRoute` enum
(`Library | History | Settings`); `BackHandler` pops to `Library`. No
navigation-compose dependency.

### `LibraryScreen` (single `LazyColumn`, existing cinematic-dark theme)

1. **Header** — "Lumen" wordmark (Sora), overflow → History, Settings.
2. **Play bar** — compact row: URL `OutlinedTextField` (prefilled from
   `PlayerPrefs.lastUrl`) + "Play" + "Open file" icon. Same actions as
   today's `SourcePicker`.
3. **Continue Watching** — section label + horizontal `LazyRow` from
   `observeContinueWatching()`. Whole section hidden when empty.
   - Card: 160×90 thumbnail (or gradient + title initials), thin accent
     progress bar (`positionMs / durationMs`), 1-line title,
     "12:34 left" / "45%". Tap → resume. Long-press → sheet:
     [Restart from beginning] / [Mark finished] / [Remove].
4. **Recent** — vertical list, `observeRecent(10)` filtered to rows not
   in Continue Watching. Same tap / long-press. "See all" →
   `HistoryScreen`.

Empty / first run: header + play bar + hint "Videos you play show up
here."

### `HistoryScreen`

`observeAll()` list, newest first, swipe-to-remove, "Clear all" in the
app bar (confirm dialog). Plain back nav.

### `SettingsScreen`

Phase 1: one field — TMDB API key (`OutlinedTextField`, saved to
DataStore `tmdb_api_key`), helper text + TMDB signup link, "Verify"
button present but disabled/no-op (Phase 2 wires it). Shipping the
screen now means Phase 2 only adds rows.

### Thumbnails

Not `ThumbnailStore` (that is the WebVTT sidecar). New
`filesDir/thumbs/*.jpg`, loaded by a small `rememberBitmap(path)` —
`BitmapFactory` on IO + an in-memory `LruCache`. No Coil dependency.

---

## Phase 2 — data model (intended shape)

`LumenDatabase` version 2, migration adds:

```kotlin
@Entity(tableName = "library_folder")
data class LibraryFolder(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val lastScannedAt: Long
)

@Entity(tableName = "library_video")
data class LibraryVideo(
    @PrimaryKey val documentUri: String,
    val folderTreeUri: String,          // FK library_folder
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val relativePath: String,           // for show/season grouping
    val metadataId: Long? = null
)

@Entity(tableName = "tmdb_metadata")
data class TmdbMetadata(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,              // MOVIE | TV
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val runtimeMinutes: Int?,
    val fetchedAt: Long
)

@Entity(tableName = "tmdb_match_cache")
data class TmdbMatchCache(
    @PrimaryKey val queryKey: String,   // normalized parsed name + year + SxxEyy
    val metadataId: Long?,              // null = confirmed no match (do not re-query)
    val attemptedAt: Long
)
```

Resume state for a `LibraryVideo` = left join `playback_history` on
`uri == documentUri`.

## Phase 2 — components (intended shape)

- `library/scan/FolderScanner` — walk a `treeUri` with `DocumentFile`
  (or `DocumentsContract` bulk query for speed), filter to video MIME
  / known extensions, diff against `library_video` (add new, drop
  missing, update size/mtime), update `lastScannedAt`. Runs on a
  `WorkManager` one-shot or a coroutine on folder-add + manual
  refresh. (WorkManager dependency decision deferred to Phase 2
  planning.)
- `library/tmdb/TmdbClient` — OkHttp; `searchMovie`, `searchTv`,
  `tvEpisode(showId, season, ep)`; key from DataStore `tmdb_api_key`;
  no key or 401 → feature disabled, surfaced in Settings.
- `library/tmdb/FilenameParser` — strip release tags / groups /
  codecs / resolutions; extract title, year, `SxxEyy` (and
  `1x02`, `Season 1 Episode 2`). Pure function, heavily table-tested.
- `library/tmdb/MetadataMatcher` — parse → `tmdb_match_cache` lookup
  (incl. negative) → query → persist `tmdb_metadata` + cache row →
  link `library_video.metadataId`. Rate-limited, background.
- UI: `FolderScreen` (poster grid, grouped by detected show →
  season → episode; movies flat), `DetailSheet` (poster + overview +
  Resume / Restart), Settings "Verify" wired, folder management
  (remove folder, rescan).

## Phase 2 — folder UI on home

`LibraryScreen` gains a **Folders** section below Recent: each
`LibraryFolder` as a card → `FolderScreen`; "+ Add folder" →
`OpenDocumentTree` + `takePersistableUriPermission` + initial scan.

---

## Testing

### Phase 1 (JUnit4, local — matches existing `app/src/test`)

- `NormalizeMediaUriTest` — table: http fragment stripping, scheme /
  host case, `content://` verbatim, `file://` verbatim, whitespace,
  query preservation, idempotency.
- `FinishedRuleTest` — near-start, near-end, mid, `durationMs == 0`
  (unknown), exactly on the 5 s boundary.
- `HistoryDaoTest` — Robolectric or in-memory Room
  (`Room.inMemoryDatabaseBuilder`, `allowMainThreadQueries` in test):
  upsert keeps `positionMs` and bumps `lastPlayedAt`; Continue
  Watching query filters `finished` and `positionMs <= 5000` and
  orders / limits; `delete` / `clear`. (Adds a Room test dependency;
  if Robolectric is unwanted, keep DAO logic thin and test the query
  predicates via a pure helper — decided at plan time.)
- `DataStoreMigrationTest` — given seeded `pos_*` + `last_url`, after
  migration the `pos_*` keys are gone, `last_url` remains,
  `history_migrated_v1` is set; second run is a no-op.

### Phase 2

- `FilenameParserTest` — large table of real-world names (movies with
  year, `S01E02`, `1x02`, dotted names, groups, multi-episode,
  ambiguous).
- `MetadataMatcherTest` — cache hit, negative-cache hit (no network
  call), miss → query → persist, 401 handling. `TmdbClient` faked.
- `FolderScannerTest` — diff logic (added / removed / modified) with a
  fake document source.

### Manual QA checklist (Phase 1)

- Play URL, background app at 0:30, reopen app → row in Continue
  Watching, tap resumes at ~0:30.
- Open a local file from a file manager, stop midway, reopen the app →
  row present, resume works (when the grant was persistable).
- File manager that does not grant persistable permission → row shows,
  tap → "reopen from file manager" dialog, Remove works.
- Finish a video to the end → leaves Continue Watching, appears in
  History as finished; reopening restarts at 0.
- Fresh install → home shows only header + play bar + hint.

## Risks / open items

- **`content://` durability** is the core UX limitation and cannot be
  fully solved app-side — it depends on the file manager setting
  `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`. Phase 1 mitigates (persist
  when possible, clear dialog when not) but cannot guarantee resume
  for every external file.
- **Room DAO testing** may pull in Robolectric (heavier CI). Fallback:
  keep query predicates in a pure helper and unit-test that. Decide at
  Phase 1 plan time.
- **Thumbnail grab** is unreliable for network sources; acceptable —
  placeholder covers it. Phase 2 TMDB posters fill most of the gap.
- **TMDB filename matching** (Phase 2) is the classic hard problem;
  negative cache + "wrong match? search manually" affordance planned,
  detailed at Phase 2 planning.
- TV flavor (future) reuses `HistoryRepository` + `LibraryRepository`
  unchanged; only the 10-foot UI is new.
