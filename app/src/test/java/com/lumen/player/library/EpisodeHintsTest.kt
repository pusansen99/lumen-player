package com.lumen.player.library

import com.lumen.player.library.scan.episodeHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeHintsTest {

    private fun hint(name: String, path: List<String> = emptyList()) = episodeHints(name, path)

    @Test fun sxxexx() {
        val h = hint("Show.Name.S02E10.1080p.WEB.mkv", listOf("Show Name"))
        assertEquals(2, h.seasonNumber); assertEquals(10, h.episodeNumber)
        assertEquals("show name", h.showKey)
    }

    @Test fun lowerAndSeparators() {
        assertEquals(1 to 2, hint("show s1e2.mkv").let { it.seasonNumber to it.episodeNumber })
        assertEquals(1 to 2, hint("show S01.E02.mkv").let { it.seasonNumber to it.episodeNumber })
        assertEquals(1 to 2, hint("show S01_E02.mkv").let { it.seasonNumber to it.episodeNumber })
    }

    @Test fun xFormat() {
        val h = hint("The Show - 3x04 - Title.mkv", listOf("The Show"))
        assertEquals(3, h.seasonNumber); assertEquals(4, h.episodeNumber)
        assertEquals("the show", h.showKey)
    }

    @Test fun seasonFolderFallback() {
        val h = hint("Episode 4.mkv", listOf("The Show", "Season 3"))
        assertEquals(3, h.seasonNumber); assertEquals(4, h.episodeNumber)
        assertEquals("the show", h.showKey)   // grandparent, since parent is "Season 3"
    }

    @Test fun seasonFolderNoEpisodeNumber() {
        val h = hint("random title.mkv", listOf("The Show", "Season 03"))
        assertEquals(3, h.seasonNumber); assertNull(h.episodeNumber)
        assertEquals("the show", h.showKey)
    }

    @Test fun specials() {
        val h = hint("Show S00E01.mkv", listOf("Show"))
        assertEquals(0, h.seasonNumber); assertEquals(1, h.episodeNumber)
    }

    @Test fun plainMovieIsNull() {
        val h = hint("The Movie (2019) 1080p BluRay.mkv", listOf("Movies"))
        assertNull(h.showKey); assertNull(h.seasonNumber); assertNull(h.episodeNumber)
    }

    @Test fun showKeyNormalisation() {
        assertEquals("mr robot", hint("Mr. Robot - S01E01.mkv", listOf("Mr.  Robot!")).showKey)
    }
}
