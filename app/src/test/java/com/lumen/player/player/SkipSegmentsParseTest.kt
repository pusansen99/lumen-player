package com.lumen.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsParseTest {

    private val store = SkipSegmentsStore()

    @Test
    fun parsesTypesAndBothKeyForms() {
        val json = """
            { "segments": [
              { "type": "intro",   "start": 8000,  "end": 98000 },
              { "type": "credits", "startMs": 2580000 },
              { "type": "recap",   "start": 0, "endMs": 40000 }
            ]}
        """.trimIndent()

        val segs = store.parse(json).sortedBy { it.type.name }
        assertEquals(3, segs.size)

        val intro = segs.first { it.type == SkipType.INTRO }
        assertEquals(8_000L, intro.startMs)
        assertEquals(98_000L, intro.endMs)

        val credits = segs.first { it.type == SkipType.CREDITS }
        assertEquals(2_580_000L, credits.startMs)
        assertNull(credits.endMs)
    }

    @Test
    fun typeAliasesMap() {
        val json = """
            { "segments": [
              { "type": "opening", "start": 1000, "end": 2000 },
              { "type": "previously", "start": 3000, "end": 4000 },
              { "type": "ending", "start": 5000 }
            ]}
        """.trimIndent()
        val types = store.parse(json).map { it.type }.toSet()
        assertEquals(setOf(SkipType.INTRO, SkipType.RECAP, SkipType.CREDITS), types)
    }

    @Test
    fun skipsUnknownTypeAndMissingStart() {
        val json = """
            { "segments": [
              { "type": "sponsor", "start": 1000, "end": 2000 },
              { "type": "intro", "end": 5000 },
              { "type": "intro", "start": 6000, "end": 9000 }
            ]}
        """.trimIndent()
        val segs = store.parse(json)
        assertEquals(1, segs.size)
        assertEquals(6_000L, segs.single().startMs)
    }

    @Test
    fun endNotAfterStartBecomesOpenEnded() {
        val json = """{ "segments": [ { "type": "intro", "start": 5000, "end": 5000 } ] }"""
        assertNull(store.parse(json).single().endMs)
    }

    @Test
    fun emptyOrMalformedYieldsNothing() {
        assertTrue(store.parse("""{ "segments": [] }""").isEmpty())
        assertTrue(store.parse("""{ }""").isEmpty())
    }

    @Test
    fun sortedByStart() {
        val json = """
            { "segments": [
              { "type": "credits", "start": 9000 },
              { "type": "intro", "start": 1000, "end": 2000 }
            ]}
        """.trimIndent()
        val segs = store.parse(json)
        assertEquals(listOf(1_000L, 9_000L), segs.map { it.startMs })
    }
}
