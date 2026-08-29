package com.lumen.player.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatsUnderAnHourAsMinutesSeconds() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:07", formatTime(7_000))
        assertEquals("1:05", formatTime(65_000))
        assertEquals("42:09", formatTime(42L * 60_000 + 9_000))
    }

    @Test
    fun formatsOverAnHourWithHours() {
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("1:02:03", formatTime(3_600_000 + 2 * 60_000 + 3_000))
    }

    @Test
    fun unknownAndNegativeAreDashes() {
        assertEquals("--:--", formatTime(C.TIME_UNSET))
        assertEquals("--:--", formatTime(-1))
    }
}
