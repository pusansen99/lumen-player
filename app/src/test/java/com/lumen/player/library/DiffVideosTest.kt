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
