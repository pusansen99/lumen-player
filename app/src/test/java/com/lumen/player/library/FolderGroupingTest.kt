package com.lumen.player.library

import com.lumen.player.library.data.LibraryVideoRow
import com.lumen.player.library.ui.groupFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderGroupingTest {

    private fun row(
        name: String, show: String? = null, season: Int? = null, ep: Int? = null,
        uri: String = name,
    ) = LibraryVideoRow(
        documentUri = uri, folderTreeUri = "t", displayName = name, sizeBytes = 0, lastModified = 0,
        relativePath = "", showKey = show, seasonNumber = season, episodeNumber = ep, metadataId = null,
        hPositionMs = null, hDurationMs = null, hFinished = null, hThumbnailPath = null,
    )

    @Test fun moviesOnly_sortedCaseInsensitive() {
        val c = groupFolder(listOf(row("banana.mkv"), row("Apple.mkv")))
        assertEquals(listOf("Apple.mkv", "banana.mkv"), c.movies.map { it.displayName })
        assertEquals(emptyList<Any>(), c.shows)
    }

    @Test fun oneShow_seasonsAscending_specialsLast() {
        val c = groupFolder(listOf(
            row("e1", show = "the show", season = 2, ep = 1),
            row("sp", show = "the show", season = 0, ep = 1),
            row("e0", show = "the show", season = 1, ep = 1),
        ))
        assertEquals(1, c.shows.size)
        assertEquals(listOf(1, 2, 0), c.shows[0].seasons.map { it.number })
    }

    @Test fun episodesSortedByNumber_nullsLast() {
        val c = groupFolder(listOf(
            row("b", show = "s", season = 1, ep = null),
            row("a", show = "s", season = 1, ep = 3),
            row("c", show = "s", season = 1, ep = 1),
        ))
        assertEquals(listOf("c", "a", "b"), c.shows[0].seasons[0].episodes.map { it.displayName })
    }

    @Test fun twoShows_alphaOrder() {
        val c = groupFolder(listOf(
            row("x", show = "zebra", season = 1, ep = 1),
            row("y", show = "alpha", season = 1, ep = 1),
        ))
        assertEquals(listOf("Alpha", "Zebra"), c.shows.map { it.displayName })
    }

    @Test fun showWithoutSeason_bucketsIntoZero() {
        val c = groupFolder(listOf(row("x", show = "s", season = null, ep = 1)))
        assertEquals(listOf(0), c.shows[0].seasons.map { it.number })
    }

    @Test fun episodeCountAcrossSeasons() {
        val c = groupFolder(listOf(
            row("a", show = "s", season = 1, ep = 1),
            row("b", show = "s", season = 1, ep = 2),
            row("c", show = "s", season = 2, ep = 1),
        ))
        assertEquals(3, c.shows[0].episodeCount)
    }
}
