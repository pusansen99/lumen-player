package com.lumen.player.library.scan

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import com.lumen.player.library.data.LibraryFolder
import com.lumen.player.library.data.LibraryVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
