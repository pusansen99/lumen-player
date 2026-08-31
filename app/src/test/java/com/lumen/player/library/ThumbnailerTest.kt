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
