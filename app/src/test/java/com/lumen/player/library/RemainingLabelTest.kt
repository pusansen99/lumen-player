package com.lumen.player.library

import com.lumen.player.library.ui.remainingLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingLabelTest {

    @Test fun showsPercentWhenDurationKnown() {
        assertEquals("45% watched", remainingLabel(positionMs = 810_000L, durationMs = 1_800_000L))
    }

    @Test fun fallsBackToElapsedWhenDurationUnknown() {
        assertEquals("13:30 in", remainingLabel(positionMs = 810_000L, durationMs = 0L))
    }

    @Test fun clampsPercentToRange() {
        assertEquals("99% watched", remainingLabel(positionMs = 1_799_999L, durationMs = 1_800_000L))
    }
}
