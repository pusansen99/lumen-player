package com.lumen.player.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun higherPatchIsNewer() {
        assertTrue(UpdateRepository.isNewer(remote = "v0.5.1", current = "0.5.0"))
    }

    @Test
    fun higherMinorIsNewer() {
        assertTrue(UpdateRepository.isNewer(remote = "0.6.0", current = "0.5.9"))
    }

    @Test
    fun higherMajorIsNewer() {
        assertTrue(UpdateRepository.isNewer(remote = "v1.0.0", current = "0.99.99"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(UpdateRepository.isNewer(remote = "v0.5.0", current = "0.5.0"))
    }

    @Test
    fun olderIsNotNewer() {
        assertFalse(UpdateRepository.isNewer(remote = "v0.4.9", current = "0.5.0"))
    }

    @Test
    fun preReleaseSuffixIsIgnored() {
        assertFalse(UpdateRepository.isNewer(remote = "v0.5.0-rc1", current = "0.5.0"))
        assertTrue(UpdateRepository.isNewer(remote = "v0.5.1-rc1", current = "0.5.0"))
    }

    @Test
    fun numericComparisonNotLexical() {
        // "10" > "9" numerically, but "10" < "9" as strings
        assertTrue(UpdateRepository.isNewer(remote = "v0.10.0", current = "0.9.0"))
    }

    @Test
    fun missingComponentsTreatedAsZero() {
        assertTrue(UpdateRepository.isNewer(remote = "v1", current = "0.9"))
        assertFalse(UpdateRepository.isNewer(remote = "v1", current = "1.0.0"))
    }
}
