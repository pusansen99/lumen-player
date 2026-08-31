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
