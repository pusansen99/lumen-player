package com.lumen.player.library

import com.lumen.player.library.data.PlaybackHistoryEntry
import com.lumen.player.library.data.isFinished
import com.lumen.player.library.data.qualifiesForContinueWatching
import com.lumen.player.library.data.resumePositionForEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHistoryRulesTest {

    private fun entry(
        position: Long,
        duration: Long = 1_800_000L,
        finished: Boolean = false,
    ) = PlaybackHistoryEntry(
        uri = "u",
        sourceType = "URL",
        title = "t",
        positionMs = position,
        durationMs = duration,
        lastPlayedAt = 0L,
        finished = finished,
        thumbnailPath = null,
        hasPersistedPermission = true,
        metadataId = null,
    )

    @Test fun notFinishedInTheMiddle() {
        assertFalse(isFinished(positionMs = 900_000L, durationMs = 1_800_000L))
    }

    @Test fun finishedWithinFiveSecondsOfTheEnd() {
        assertTrue(isFinished(positionMs = 1_796_000L, durationMs = 1_800_000L))
    }

    @Test fun exactlyOnTheBoundaryIsNotFinished() {
        // positionMs == durationMs - NEAR_EDGE_MS -> strictly greater test fails -> not finished
        assertFalse(isFinished(positionMs = 1_795_000L, durationMs = 1_800_000L))
    }

    @Test fun unknownDurationIsNeverFinished() {
        assertFalse(isFinished(positionMs = 10_000L, durationMs = 0L))
        assertFalse(isFinished(positionMs = 10_000L, durationMs = -1L))
    }

    @Test fun continueWatchingNeedsUnfinishedAndPastFiveSeconds() {
        assertTrue(qualifiesForContinueWatching(entry(position = 6_000L)))
        assertFalse(qualifiesForContinueWatching(entry(position = 4_000L)))
        assertFalse(qualifiesForContinueWatching(entry(position = 6_000L, finished = true)))
    }

    @Test fun resumePositionIsZeroForNullOrFinished() {
        assertEquals(0L, resumePositionForEntry(null))
        assertEquals(0L, resumePositionForEntry(entry(position = 500_000L, finished = true)))
    }

    @Test fun resumePositionIsStoredPositionOtherwise() {
        assertEquals(500_000L, resumePositionForEntry(entry(position = 500_000L)))
    }
}
