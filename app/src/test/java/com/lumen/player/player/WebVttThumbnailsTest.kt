package com.lumen.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVttThumbnailsTest {

    @Test
    fun parseTimeAcceptsBothForms() {
        assertEquals(0L, parseTime("00:00:00.000"))
        assertEquals(2_000L, parseTime("00:00:02.000"))
        assertEquals(65_500L, parseTime("01:05.500"))
        assertEquals(3_661_000L, parseTime("01:01:01.000"))
    }

    @Test
    fun parseTimeRejectsGarbage() {
        assertNull(parseTime("nope"))
        assertNull(parseTime(""))
    }

    @Test
    fun parsesSpriteSheetCues() {
        val vtt = """
            WEBVTT

            00:00:00.000 --> 00:00:02.000
            storyboard.jpg#xywh=0,0,180,101

            00:00:02.000 --> 00:00:04.000
            storyboard.jpg#xywh=180,0,180,101
        """.trimIndent()

        val track = parseWebVtt(vtt, baseUrl = "https://cdn.example.com/vid/thumbnails.vtt")
        assertNotNull(track)

        val first = track!!.cueAt(500)!!
        assertEquals("https://cdn.example.com/vid/storyboard.jpg", first.imageUrl)
        assertEquals(0, first.x)
        assertTrue(first.hasRect)
        assertEquals(180, first.w)

        val second = track.cueAt(3_000)!!
        assertEquals(180, second.x)
    }

    @Test
    fun handlesOneImagePerCue() {
        val vtt = """
            WEBVTT

            00:00:00.000 --> 00:00:05.000
            thumbs/0001.jpg

            00:00:05.000 --> 00:00:10.000
            thumbs/0002.jpg
        """.trimIndent()

        val track = parseWebVtt(vtt, baseUrl = "https://cdn.example.com/vid/thumbs.vtt")!!
        val cue = track.cueAt(6_000)!!
        assertEquals("https://cdn.example.com/vid/thumbs/0002.jpg", cue.imageUrl)
        assertTrue(!cue.hasRect)
    }

    @Test
    fun nonVttReturnsNull() {
        assertNull(parseWebVtt("not a vtt file", baseUrl = "https://x/y.vtt"))
        assertNull(parseWebVtt("WEBVTT\n\n(no cues)", baseUrl = "https://x/y.vtt"))
    }

    @Test
    fun cueAtClampsToNearestPrecedingCue() {
        val vtt = """
            WEBVTT

            00:00:00.000 --> 00:00:02.000
            s.jpg#xywh=0,0,10,10

            00:00:02.000 --> 00:00:04.000
            s.jpg#xywh=10,0,10,10
        """.trimIndent()
        val track = parseWebVtt(vtt, baseUrl = "https://x/s.vtt")!!
        // past the end of the last cue -> still returns the last cue
        assertEquals(10, track.cueAt(99_000)!!.x)
    }
}
