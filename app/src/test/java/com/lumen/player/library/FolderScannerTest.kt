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
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [36])
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

    // A real SAF tree uri needs an authority segment ("content://<authority>/tree/<docId>");
    // the bare "content://tree/root" from the brief makes DocumentsContract.getTreeDocumentId throw.
    private val folder = LibraryFolder("content://com.lumen.documents/tree/root", "root", 0, 0, 0)

    /** Wire a resolver whose query() answers a documentId -> cursor map, keyed by the child-doc-id in the uri. */
    private fun resolverFor(byDocId: Map<String, MatrixCursor?>): ContentResolver {
        val r = mock<ContentResolver>()
        whenever(r.query(any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenAnswer { inv ->
            val uri = inv.getArgument<Uri>(0)
            // buildChildDocumentsUriUsingTree(...) => .../document/<parentDocId>/children
            val segs = uri.pathSegments
            val docId = if (segs.size >= 2) segs[segs.size - 2] else ""
            // explicit-null entries must win over the "*root*" fallback
            if (byDocId.containsKey(docId)) byDocId[docId] else byDocId["*root*"]
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
