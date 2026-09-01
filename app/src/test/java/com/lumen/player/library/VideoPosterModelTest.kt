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
