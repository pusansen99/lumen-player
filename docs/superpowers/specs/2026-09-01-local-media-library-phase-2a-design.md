# Local Media Library — Phase 2a: Folder Browsing — Design

Date: 2026-09-01
Status: approved for planning
Branch: `feat/library-folders`
Predecessor: `docs/superpowers/specs/2026-08-31-local-media-library-design.md` (Phase 1, merged in PR #23)

## Problem

Phase 1 records what you play into `playback_history` and shows it on the
home screen (Continue Watching + Recent). There is still no way to
*browse* what is on the device — you can only resume something you have
already opened once.

Phase 2a adds an on-device, offline library: point Lumen at folders via
the system folder picker, and browse their videos as a poster grid, with
TV files grouped show → season → episode. No network. Resume state is
joined in from the Phase 1 history.

TMDB metadata (real posters, titles, overviews, the "wrong match?" fixer,
and matching for one-off external plays) is **Phase 2b** — a separate
spec/plan on top of this.

## Decisions (locked)

| Question | Decision |
|---|---|
| Scan runtime | Plain coroutine on `Dispatchers.IO`, triggered by folder-add and a manual rescan. No `WorkManager`. |
| Remote image loading | Add **Coil 3** (`io.coil-kt.coil3`). Replaces Phase 1's hand-rolled `rememberBitmap` + `LruCache`. |
| TMDB match aggressiveness | (Phase 2b) auto-match in the background + a manual "fix match" search dialog. |
| Folder UI scope | Poster grid + show → season → episode grouping + a `DetailSheet`. |
| Folder access | SAF `ACTION_OPEN_DOCUMENT_TREE`, persistable permission. No `READ_MEDIA_VIDEO`, no `MediaStore`. |
| Migration testing | Robolectric (`testImplementation`) so `MIGRATION_1_2` is covered under `testDebugUnitTest` / CI. No `androidTest` source set. |

## Global constraints (carry into the plan)

- Package root `com.lumen.player`; Phase 2a code under
  `com.lumen.player.library` (`data/`, `scan/`, `ui/`).
- `minSdk = 36`, `targetSdk = 37`, `compileSdk = 37` — unchanged.
- Version from git tags; never hand-edit.
- Unit tests: pure JVM JUnit4 under `app/src/test`, `org.junit.Assert.*`
  static imports, matching the existing style. Robolectric is allowed
  **only** for the one migration test.
- No new signing/keystore literals in tracked files.
- Cinematic-dark UI: bg `#0B0B0D`, accent `#4C8DFF`, secondary text
  `#A1A1AA`. Reuse `com.lumen.player.ui.theme` and the colour vals in
  `PlayerControls.kt` / `HistoryCards.kt`.
- Every commit compiles; `assembleDebug` + `assembleRelease` (R8) +
  `lintDebug` + `testDebugUnitTest` green at the end of each task.
- `main` is protected — feature branch → PR → green `build` → owner merge.
- Caveman session style does not apply to committed code, comments,
  KDoc, or commit messages — those are normal English.

## Dependencies added

Version catalog (`gradle/libs.versions.toml`), exact pins:

```toml
[versions]
coil = "3.6.0"
robolectric = "4.16.1"

[libraries]
coil-compose        = { group = "io.coil-kt.coil3", name = "coil-compose",        version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
coil-video          = { group = "io.coil-kt.coil3", name = "coil-video",          version.ref = "coil" }
robolectric         = { group = "org.robolectric",  name = "robolectric",         version.ref = "robolectric" }
```

`app/build.gradle.kts`:
```kotlin
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)   // 2b uses it for image.tmdb.org; harmless now
implementation(libs.coil.video)
testImplementation(libs.robolectric)
testOptions { unitTests.isIncludeAndroidResources = true }  // Robolectric
```

R8: Coil ships consumer ProGuard rules; verify `assembleRelease` at the
task that adds the dependency. Robolectric is test-only, not shipped.

---

## Section 1 — data model + DB v2 migration

### `LumenDatabase` → version 2

`@Database(entities = [PlaybackHistoryEntry::class, LibraryFolder::class, LibraryVideo::class], version = 2, exportSchema = true)`.
`playback_history` is unchanged. Register `MIGRATION_1_2` in
`LumenDatabase.get()` via `.addMigrations(MIGRATION_1_2)`. **No
`fallbackToDestructiveMigration`.**

`MIGRATION_1_2` (hand-written, in `LumenDatabase.kt` or a
`Migrations.kt` sibling):

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `library_folder` (
              `treeUri` TEXT NOT NULL,
              `displayName` TEXT NOT NULL,
              `addedAt` INTEGER NOT NULL,
              `lastScannedAt` INTEGER NOT NULL,
              `videoCount` INTEGER NOT NULL,
              PRIMARY KEY(`treeUri`))
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `library_video` (
              `documentUri` TEXT NOT NULL,
              `folderTreeUri` TEXT NOT NULL,
              `displayName` TEXT NOT NULL,
              `sizeBytes` INTEGER NOT NULL,
              `lastModified` INTEGER NOT NULL,
              `relativePath` TEXT NOT NULL,
              `showKey` TEXT,
              `seasonNumber` INTEGER,
              `episodeNumber` INTEGER,
              `metadataId` INTEGER,
              PRIMARY KEY(`documentUri`),
              FOREIGN KEY(`folderTreeUri`) REFERENCES `library_folder`(`treeUri`)
                ON UPDATE NO ACTION ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_video_folderTreeUri` ON `library_video` (`folderTreeUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_video_showKey` ON `library_video` (`showKey`)")
    }
}
```

The exact column ordering / SQL must match what Room's generator emits
for the entities below — the plan's task regenerates `schemas/…/2.json`
via KSP and reconciles the migration SQL against `2.json`'s
`createSql` before committing.

### Entities (`library/data/`)

```kotlin
@Entity(tableName = "library_folder")
data class LibraryFolder(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val lastScannedAt: Long,   // 0 until the first scan completes
    val videoCount: Int,       // denormalised; refreshed each scan (home card subtitle)
)

@Entity(
    tableName = "library_video",
    foreignKeys = [ForeignKey(
        entity = LibraryFolder::class,
        parentColumns = ["treeUri"], childColumns = ["folderTreeUri"],
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
    val relativePath: String,     // path under the tree root, '/'-joined, excludes the file name
    val showKey: String?,         // normalised show name when an episode pattern matched; else null (= movie)
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val metadataId: Long? = null, // always null in 2a; 2b populates
)
```

`showKey` / `seasonNumber` / `episodeNumber` are filled by a **small
regex in the scanner** in 2a (Section 2). The full release-tag-stripping
`FilenameParser` is 2b; the columns exist now so 2b needs no further
migration for them.

### `LibraryDao` (`library/data/LibraryDao.kt`)

```kotlin
@Dao
interface LibraryDao {
    // folders
    @Upsert suspend fun upsertFolder(folder: LibraryFolder)
    @Query("DELETE FROM library_folder WHERE treeUri = :treeUri") suspend fun deleteFolder(treeUri: String)
    @Query("SELECT * FROM library_folder ORDER BY displayName COLLATE NOCASE") fun observeFolders(): Flow<List<LibraryFolder>>
    @Query("SELECT * FROM library_folder WHERE treeUri = :treeUri") suspend fun folder(treeUri: String): LibraryFolder?
    @Query("UPDATE library_folder SET lastScannedAt = :at, videoCount = :count WHERE treeUri = :treeUri")
    suspend fun setFolderScanned(treeUri: String, at: Long, count: Int)

    // videos
    @Upsert suspend fun upsertVideos(videos: List<LibraryVideo>)
    @Query("SELECT * FROM library_video WHERE folderTreeUri = :treeUri") suspend fun videosInFolder(treeUri: String): List<LibraryVideo>
    @Query("DELETE FROM library_video WHERE folderTreeUri = :treeUri AND documentUri NOT IN (:keepUris)")
    suspend fun deleteVideosNotIn(treeUri: String, keepUris: List<String>)

    // videos + resume state, projected
    @Query("""
        SELECT v.*, h.positionMs AS h_positionMs, h.durationMs AS h_durationMs,
               h.finished AS h_finished, h.thumbnailPath AS h_thumbnailPath
        FROM library_video v
        LEFT JOIN playback_history h ON h.uri = v.documentUri
        WHERE v.folderTreeUri = :treeUri
    """)
    fun observeFolderRows(treeUri: String): Flow<List<LibraryVideoRow>>

    @Transaction
    suspend fun applyScan(treeUri: String, found: List<LibraryVideo>, scannedAt: Long) {
        upsertVideos(found)
        deleteVideosNotIn(treeUri, found.map { it.documentUri })
        setFolderScanned(treeUri, scannedAt, found.size)
    }
}
```

`LibraryVideoRow` — a POJO (not `@Embedded`, to keep the join columns
flat and nullable): `@ColumnInfo` names `h_positionMs` etc., mapped to a
small `data class LibraryVideoRow(...)` with a computed
`val positionMs get() = h_positionMs ?: 0L`, `durationMs`, `finished`
(`(h_finished ?: 0) != 0`), `thumbnailPath` (`h_thumbnailPath`).
Provide the `LibraryVideo` fields directly on the POJO.

### `LumenDatabase` also exposes `library(): LibraryDao`.

### Migration test — `LibraryMigrationTest` (Robolectric, `testDebugUnitTest`)

`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [...])`. Use
`Room.databaseBuilder` on a temp file:
1. Create the DB at v1 by building with only `PlaybackHistoryEntry`
   (or execute the committed `1.json` `createSql` against a raw
   `SupportSQLiteDatabase`), insert one `playback_history` row.
2. Close, reopen with the v2 `LumenDatabase` + `MIGRATION_1_2`.
3. Assert: `library_folder` and `library_video` tables exist with the
   expected columns (PRAGMA `table_info`), the FK and both indices
   exist (PRAGMA `foreign_key_list` / `index_list`), and the
   `playback_history` row still reads back intact.

If a full Room-open proves awkward under Robolectric, fall back to
driving `MIGRATION_1_2.migrate(db)` directly against a
`FrameworkSQLiteOpenHelper`-backed `SupportSQLiteDatabase` and asserting
via PRAGMA — decided at task time; either way the migration SQL is
exercised, not just declared.

Commit `app/schemas/com.lumen.player.library.data.LumenDatabase/2.json`.

---

## Section 2 — folder access + scanner

### `LibraryRepository` (`library/LibraryRepository.kt`)

Singleton (`LibraryRepository.get(context)` — same shape as
`HistoryRepository`), holds `appContext`, `LibraryDao`, a
`CoroutineScope(SupervisorJob() + Dispatchers.IO)`, a
`MutableMap<String, Mutex>` for per-folder scan serialisation, and a
`MutableStateFlow<Set<String>>` of `treeUri`s currently scanning.

```kotlin
val folders: Flow<List<LibraryFolder>>            // dao.observeFolders()
val scanning: StateFlow<Set<String>>              // in-flight treeUris
fun folderRows(treeUri: String): Flow<List<LibraryVideoRow>>   // dao.observeFolderRows

sealed interface AddFolderResult { data class Ok(val treeUri: String) : ...; object PermissionDenied : ... }
suspend fun addFolder(treeUri: Uri): AddFolderResult
suspend fun removeFolder(treeUri: String)
suspend fun rescan(treeUri: String): ScanOutcome
suspend fun rescanAll()
```

`addFolder`:
1. `runCatching { contentResolver.takePersistableUriPermission(treeUri, FLAG_GRANT_READ_URI_PERMISSION) }`
   → on failure return `PermissionDenied`.
2. `displayName` = `DocumentFile.fromTreeUri(appContext, treeUri)?.name`
   ?: last path segment of `treeUri`.
3. `dao.upsertFolder(LibraryFolder(treeUri.toString(), name, now, 0, 0))`.
4. Launch a scan on the repo scope (fire-and-forget); return
   `Ok(treeUri.toString())` immediately so the UI can show the row with
   "Scanning…".

`removeFolder`:
1. `dao.deleteFolder(treeUri)` — FK cascade drops the `library_video`
   rows.
2. `runCatching { contentResolver.releasePersistableUriPermission(treeUri.toUri(), FLAG_GRANT_READ_URI_PERMISSION) }`.
3. `playback_history` is untouched.

`rescan(treeUri)`:
- Acquire the folder's `Mutex`. Add to `scanning`. `try/finally` remove.
- `folder = dao.folder(treeUri) ?: return ScanOutcome.Empty`.
- `result = FolderScanner.scan(appContext.contentResolver, folder)`.
- On `result is ScanOutcome.PermissionLost` → return it (don't wipe the
  folder's rows — the grant may come back).
- On `Ok(found)` → `dao.applyScan(treeUri, found, now)`; return
  `Ok(found.size)`.

`ScanOutcome { data class Ok(count: Int); object PermissionLost; object Empty }`.

### `FolderScanner` (`library/scan/FolderScanner.kt`)

```kotlin
object FolderScanner {
    suspend fun scan(resolver: ContentResolver, folder: LibraryFolder): ScanOutcome
}
```

- On `Dispatchers.IO`.
- BFS with an explicit queue of `(documentId, relativeSegments)`.
  Root children uri:
  `DocumentsContract.buildChildDocumentsUriUsingTree(folder.treeUri.toUri(), DocumentsContract.getTreeDocumentId(folder.treeUri.toUri()))`.
  For each subsequent dir:
  `buildChildDocumentsUriUsingTree(treeUri, dirDocumentId)`.
- Per level, one `resolver.query(childrenUri, projection, null, null, null)`
  where projection =
  `[DOCUMENT_ID, DISPLAY_NAME, MIME_TYPE, SIZE, LAST_MODIFIED]`.
  `query` returning `null` on the **root** level → `PermissionLost`.
  `null` on a deeper level → skip that subtree, keep going.
- A row with `MIME_TYPE == DocumentsContract.Document.MIME_TYPE_DIR`
  and depth < `MAX_DEPTH` (12) and its documentId not already visited →
  enqueue.
- A row that is a video → `MIME_TYPE` starts `video/`, **or** its
  name's extension ∈ `VIDEO_EXT` (`mkv mp4 m4v webm mov avi ts m3u8 mpd`)
  — build a `LibraryVideo`:
  - `documentUri` = `DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId).toString()`
  - `relativePath` = the current segment stack joined by `/`
  - `showKey` / `seasonNumber` / `episodeNumber` via `episodeHints(name, relativeSegments)` (below)
- Guard: a visited-set on documentId; hard cap `MAX_FILES` (say 20_000)
  → stop, return what was found (log a warning).
- Return `ScanOutcome.Ok(found)` (may be empty).

`episodeHints(fileName: String, pathSegments: List<String>): EpisodeHint`
— pure, unit-tested:
- `EPISODE_RX = Regex("""[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})""")` on the
  file name → season, episode.
- fallback `Regex("""\b(\d{1,2})x(\d{1,3})\b""")` (`1x02`).
- fallback: a path segment matching `Regex("""(?i)^season[ ._-]?(\d{1,3})$""")`
  gives the season; episode then from a leading/`Ep\d+` number in the
  file name; if none, `episode = null` but still an episode of that show.
- `showKey` when any of the above hit: take the **parent folder name**
  if it isn't a "Season N" segment, else the grandparent; lowercase,
  strip non-alphanumeric to single spaces, trim. If no usable folder
  name, use the file name with the `SxxEyy`/`1x02` and trailing junk
  removed.
- No hit → `EpisodeHint(null, null, null)` → the video is a movie.

### `diffVideos` (pure, in `FolderScanner.kt` or a sibling)

`fun diffVideos(existing: List<LibraryVideo>, found: List<LibraryVideo>): DiffResult`
where `DiffResult(upsert: List<LibraryVideo>, deleteUris: List<String>)`:
- index `existing` by `documentUri`.
- `upsert` = every `found` row that is new, or whose
  `sizeBytes` / `lastModified` / `relativePath` / `showKey` /
  `seasonNumber` / `episodeNumber` / `displayName` differs — **carrying
  forward `existing.metadataId`** on changed rows.
- `deleteUris` = `existing` uris not in `found`.
- Unchanged rows are neither upserted nor deleted.

`LibraryRepository` uses `diffVideos` to compute the arg to
`dao.applyScan` (so `applyScan` gets `upsert` list, not the full
`found`); `deleteVideosNotIn` already covers deletion by "keep" set, so
`applyScan` stays as written and the diff only trims the upsert list.

### Tests

`FolderScannerTest` (pure JVM, a fake `ContentResolver`):
- Wrap the resolver behind a tiny test double that returns
  `MatrixCursor`s for known children uris. (Only `query` is used.)
- Cases: files found at root + nested; dirs recursed; non-video
  skipped; `relativePath` correct for depth 0/1/3; `MAX_DEPTH` honoured;
  root `query` null → `PermissionLost`; deep `query` null → subtree
  skipped, rest found; visited-set prevents a cycle.

`EpisodeHintsTest` (pure): `S01E02`, `s1e2`, `S01.E02`, `1x02`,
`Show.Name.S02E10.1080p.mkv`, `Season 3/Episode 4.mkv`,
`Season 03/Show - 04 - name.mkv`, a plain movie
`The Movie (2019) 1080p.mkv` → `showKey == null`, specials
`S00E01`.

`DiffVideosTest` (pure): added / removed / size-changed / mtime-changed
/ path-changed / episode-renumbered / unchanged / `metadataId` carried
forward.

---

## Section 3 — thumbnails + Coil

### App `ImageLoader`

`library/ui/LumenImageLoader.kt` — a singleton
`fun imageLoader(context: Context): ImageLoader` building once with:
- `MemoryCache` (Coil default fraction).
- `DiskCache` at `context.cacheDir/coil`.
- `add(VideoFrameDecoder.Factory())` (from `coil-video`) so a video
  `content://` model yields a frame.
- (2b will `add` an OkHttp network component + `image.tmdb.org`
  mapping; not in 2a.)

Wire into Compose in `MainActivity` (or the theme wrapper):
`setSingletonImageLoaderFactory { imageLoader(it) }` (Coil 3 API) so
every `AsyncImage` uses it.

### `HistoryCards` — replace `rememberBitmap`

Delete `rememberBitmap(path)` and its module `LruCache`. In
`ContinueWatchingCard` / `HistoryRow`, the poster becomes:

```kotlin
AsyncImage(
    model = entry.thumbnailPath,     // a file path string; null renders the error slot
    contentDescription = null,
    contentScale = ContentScale.Crop,
    error = painterResource-or-Movie-icon placeholder,
    modifier = ...,
)
```

Keep the existing `Icons.Filled.Movie` placeholder as the `error` /
`fallback` slot. This touches Phase 1 code but is a clean deletion +
substitution, no behaviour change for a played video.

### Poster model resolution — `videoPosterModel` (pure, tested)

`fun videoPosterModel(row: LibraryVideoRow): Any?`:
1. `row.metadataId` → (2b) a TMDB poster URL. **In 2a `metadataId` is
   always null**, so this branch is dead code guarded for 2b; return a
   sentinel the caller ignores, or just skip — keep the function
   returning `thumbnailPath ?: documentUri`.
2. `row.thumbnailPath` (Phase 1 frame for a played video) → that path.
3. else `row.documentUri` (a `content://` video → `VideoFrameDecoder`
   grabs a frame).

`FolderScreen` tiles:
```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(videoPosterModel(row))
        .videoFramePercent(0.15)      // ignored for non-video models
        .crossfade(true)
        .build(),
    ...
)
```

`VideoPosterModelTest` (pure): thumbnailPath present → path;
thumbnailPath null → documentUri; (2b placeholder) metadataId path
covered when 2b lands.

### No new permission

`MediaMetadataRetriever` (inside `VideoFrameDecoder`) on a persisted
tree-child `content://` works with the SAF grant already held.

---

## Section 4 — UI

### Navigation (`PlayerScreen.kt`)

`enum class HomeRoute { Library, History, Settings, Folder }`.
Add `var folderRouteUri by rememberSaveable { mutableStateOf<String?>(null) }`.
`when (homeRoute)` gains a `Folder ->` branch rendering
`FolderScreen(folderRouteUri!!, onPlay = <same lambda as Library>, onBack = { homeRoute = HomeRoute.Library })`.
`BackHandler(enabled = homeRoute == HomeRoute.Folder) { homeRoute = HomeRoute.Library }`.
Tapping a folder card sets `folderRouteUri` then `homeRoute = Folder`.

### `LibraryScreen` — "Folders" section

Between "Recent" and the empty-state hint. Hidden entirely when
`folders` is empty **and** no add is in progress.

- Header row: `Text("Folders")` + `TextButton("＋ Add folder")`.
  The button launches
  `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`;
  on a non-null uri → `scope.launch { libraryRepo.addFolder(uri) }`;
  on `PermissionDenied` show a `Toast`/snackbar "Couldn't get lasting
  access to that folder."
- `libraryRepo.folders.collectAsState()` +
  `libraryRepo.scanning.collectAsState()` → one row per folder:
  folder icon · `displayName` · subtitle:
  - `treeUri in scanning` → "Scanning…"
  - `lastScannedAt == 0L` (attempted, not scanning, still zero) → keep
    "Scanning…" only while in `scanning`; once done with 0 videos →
    "No videos"
  - else → `"$videoCount videos"`
  - (a `PermissionLost` on last rescan is surfaced via a per-repo
    `StateFlow<Set<String>> foldersWithError`; row shows
    "Can't read — long-press to remove")
- Tap → open `FolderScreen`.
- Long-press → dropdown: **Rescan** (`scope.launch { libraryRepo.rescan(uri) }`),
  **Remove folder** → confirm `AlertDialog`
  ("Remove this folder? Its videos leave the library. Watch history is
  kept.") → `libraryRepo.removeFolder(uri)`.

### `FolderScreen` (`library/ui/FolderScreen.kt`)

`FolderScreen(treeUri: String, onPlay: (rawUri: String, label: String, type: SourceType, hasPersistedPermission: Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)`.

- Reads `folder` (one-shot via a `produceState` on
  `libraryRepo` — or add `observeFolder(treeUri)` to the DAO) for the
  title; `libraryRepo.folderRows(treeUri).collectAsState(emptyList())`
  for the body; `libraryRepo.scanning` for the spinner.
- Top bar: back arrow · folder `displayName` · overflow → "Rescan".
- Body: `groupFolder(rows)` → `FolderContents`, rendered in a
  `LazyVerticalGrid(GridCells.Adaptive(112.dp))`:
  - **Movies** section (only if non-empty): `item` header "Movies"
    spanning `maxLineSpan`, then a poster tile per movie —
    2:3 `AsyncImage`, title (2 lines max), a 3.dp accent resume bar at
    the bottom when `positionMs > 0 && !finished`.
  - **Shows**: for each `Show`, a full-width (`span = maxLineSpan`)
    header row — chevron + show `displayName` + episode count —
    toggling an expansion state
    (`rememberSaveable(saver = ...) { mutableStateMapOf<String, Boolean>() }`
    keyed by `showKey`, default collapsed). When expanded: per `Season`
    a full-width sub-header "Season N" (or "Specials" for 0), then each
    episode as a **full-width list row** (not a tile): 16:9
    `AsyncImage` thumb · `"S01E02 · <name>"` (or just `<name>` when
    `episodeNumber == null`) · resume bar.
  - Tapping any tile / episode row opens `DetailSheet` for that row
    (state: `var sheetRow by remember { mutableStateOf<LibraryVideoRow?>(null) }`).
- States: not-yet-scanned (`treeUri in scanning && rows empty`) →
  centered `CircularProgressIndicator`. Scanned, empty → "No videos in
  this folder." + a "Rescan" button.

### `groupFolder` (pure, tested) — `library/ui/FolderGrouping.kt`

```kotlin
data class FolderContents(val movies: List<LibraryVideoRow>, val shows: List<Show>)
data class Show(val showKey: String, val displayName: String, val episodeCount: Int, val seasons: List<Season>)
data class Season(val number: Int, val episodes: List<LibraryVideoRow>)   // number = seasonNumber ?: 0

fun groupFolder(rows: List<LibraryVideoRow>): FolderContents
```

- `movies` = rows with `showKey == null`, sorted by `displayName`
  `COLLATE NOCASE`-ish (case-insensitive).
- `shows` = rows with `showKey != null` grouped by `showKey`; per show,
  `displayName` = a title-cased rendering of `showKey` (2b overrides
  with the real TMDB name); `seasons` grouped by `seasonNumber ?: 0`,
  sorted ascending (0/"Specials" **last**, not first); episodes sorted
  by `episodeNumber` (nulls last) then `displayName`.
- Shows sorted by `displayName` case-insensitive.

### `DetailSheet` (`library/ui/DetailSheet.kt`)

`DetailSheet(row: LibraryVideoRow, onPlay: (...) -> Unit, onDismiss: () -> Unit)`.

- Same in-player-panel idiom as `TrackSelectionSheet` (a `Box` +
  `Surface` sliding from the bottom, drag-handle to dismiss — **not**
  `ModalBottomSheet`), so it is consistent with the rest of the app and
  survives immersive mode when reached mid-session later.
- Content: `AsyncImage` poster/frame (`videoPosterModel(row)`), title
  (`displayName`, or `"S01E02 · name"` for an episode), `relativePath`
  in a small monospace style, formatted file size
  (`android.text.format.Formatter.formatShortFileSize`).
- Actions:
  - **Resume from m:ss** — shown only when `positionMs > 0 && !finished`.
    Calls `onPlay(row.documentUri, label, SourceType.SAF_FILE, true)`;
    Phase 1's `startSession` returns the stored position, so no extra
    seek call needed here.
  - **Play from start** — `scope.launch { HistoryRepository.get(ctx).restart(row.documentUri) }`
    then `onPlay(...)`.
- No "remove" — removal is per-folder.

### Tests

`FolderGroupingTest` (pure): movies only; one show / one season; one
show / multiple seasons ordered; specials (season 0) sorted last;
episodes out of order → sorted by number; `episodeNumber == null` → sorts
after numbered, by name; two shows → alpha order; a row with `showKey`
but no season → season 0 bucket.

`VideoPosterModelTest` (Section 3).

No Compose UI tests (consistent with Phase 1).

---

## Migration / rollout

- One PR for all of Phase 2a on `feat/library-folders`.
- DB v1 → v2 is additive; no `playback_history` change; existing installs
  migrate on first open, keep all history.
- `assembleRelease` must pass at the task that adds Coil (R8 consumer
  rules) — if R8 strips a Coil decoder, add the documented
  `-keep` from Coil's README to `app/proguard-rules.pro` and note it.

## Testing summary (Phase 2a)

Pure JVM JUnit4 unless noted:
- `LibraryMigrationTest` — **Robolectric** — v1→v2 migration applies,
  new tables/FK/indices present, `playback_history` data survives.
- `EpisodeHintsTest` — filename/path → show/season/episode.
- `FolderScannerTest` — BFS walk over a fake `ContentResolver`:
  discovery, recursion, skip non-video, `relativePath`, depth cap,
  permission-lost at root vs deep, cycle guard.
- `DiffVideosTest` — add/remove/change/unchanged, `metadataId` carried.
- `FolderGroupingTest` — grouping + ordering.
- `VideoPosterModelTest` — poster model resolution order.

Manual QA (device/emulator, in the final task):
- Add a folder via the picker → row shows "Scanning…" → then
  "N videos".
- Open it → grid renders; a nested "Show/Season 01/…SxxEyy…" tree
  groups under show → season → episodes; loose files show as movies.
- Frame thumbnails appear for un-played videos; a previously-played
  video shows its Phase 1 thumb.
- Tap a tile → `DetailSheet`; "Play from start" plays at 0; play a bit,
  back out, reopen the sheet → "Resume from m:ss" now shown and works.
- Long-press folder → Rescan (add/delete a file on device first, see
  the grid update) and Remove (grid entry + its videos gone, a played
  file still in Continue Watching / History).
- Revoke the folder's access (or remove the SD card) → rescan →
  "Can't read — long-press to remove", existing rows retained.
- v1→v2 upgrade path: install the pre-merge build, play something,
  install this build → history intact, folders section present.

## Risks / open items

- **Big trees**: the BFS is one `query` per directory; a library with
  thousands of folders could take seconds. Acceptable for v1 (scan is
  backgrounded, UI shows "Scanning…"); a `DocumentsContract` bulk
  recursive query is not available pre-baseline. `MAX_FILES` guard
  prevents pathological runaway.
- **`showKey` heuristic** will mis-group some releases (e.g. anime with
  absolute numbering, movies in a folder named like a show). 2b's real
  `FilenameParser` + TMDB match will correct display; 2a grouping is
  best-effort and the `DetailSheet` always shows the true path.
- **Coil replacing `rememberBitmap`** touches Phase 1 card code — kept
  to a mechanical swap; the `error` slot preserves the placeholder.
- **Robolectric in CI** adds ~seconds to `testDebugUnitTest` and a
  test-only dependency; accepted to get the migration covered without an
  `androidTest` source set.
- Phase 2b depends on this: `LibraryVideo.metadataId`, the
  `videoPosterModel` metadata branch, and the `Show.displayName`
  override are all stubbed here for 2b to fill.
