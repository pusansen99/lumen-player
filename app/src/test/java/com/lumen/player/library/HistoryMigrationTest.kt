package com.lumen.player.library

import com.lumen.player.player.legacyResumeKeyNames
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMigrationTest {

    @Test fun selectsOnlyPosPrefixedKeys() {
        val all = setOf("pos_123", "pos_-456", "last_url", "tmdb_api_key", "history_migrated_v1")
        assertEquals(setOf("pos_123", "pos_-456"), legacyResumeKeyNames(all))
    }

    @Test fun emptyWhenNoLegacyKeys() {
        assertEquals(emptySet<String>(), legacyResumeKeyNames(setOf("last_url", "tmdb_api_key")))
    }

    @Test fun doesNotMatchSubstringInMiddle() {
        assertEquals(emptySet<String>(), legacyResumeKeyNames(setOf("x_pos_1", "position")))
    }
}
