package com.grepdroid.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMatchesTest {

    private fun frames(vararg pairs: Pair<Long, String>) = pairs.map { (t, text) -> FrameOcr(t, text) }

    @Test
    fun `blank query has no matches`() {
        assertEquals(emptyList<LongRange>(), VideoMatches.findRanges(frames(0L to "hello"), "  "))
    }

    @Test
    fun `single hit spans one frame interval`() {
        val result = VideoMatches.findRanges(frames(0L to "intro", 3000L to "Hello World"), "hello")
        assertEquals(listOf(3000L until 4000L), result)
    }

    @Test
    fun `consecutive hits merge into one range`() {
        val result = VideoMatches.findRanges(
            frames(1000L to "total 42", 2000L to "total 42", 3000L to "total 42", 4000L to "other"),
            "total"
        )
        assertEquals(listOf(1000L until 4000L), result)
    }

    @Test
    fun `gap between hits splits ranges`() {
        val result = VideoMatches.findRanges(
            frames(0L to "error", 1000L to "ok", 2000L to "ok", 5000L to "ERROR again"),
            "error"
        )
        assertEquals(listOf(0L until 1000L, 5000L until 6000L), result)
    }

    @Test
    fun `whole-query hits win over single-word hits`() {
        val result = VideoMatches.findRanges(
            frames(0L to "foo", 4000L to "foo bar", 8000L to "bar"),
            "foo bar"
        )
        assertEquals(listOf(4000L until 5000L), result)
    }

    @Test
    fun `falls back to any query word when the whole query never appears`() {
        val result = VideoMatches.findRanges(
            frames(0L to "foo", 4000L to "nothing", 8000L to "bar"),
            "foo bar"
        )
        assertEquals(listOf(0L until 1000L, 8000L until 9000L), result)
    }

    @Test
    fun `out-of-order frames are sorted`() {
        val result = VideoMatches.findRanges(frames(2000L to "x", 1000L to "x"), "x")
        assertEquals(listOf(1000L until 3000L), result)
    }

    @Test
    fun `custom frame interval controls range length and merging`() {
        val result = VideoMatches.findRanges(frames(0L to "x", 2000L to "x"), "x", frameIntervalMs = 2000L)
        assertEquals(listOf(0L until 4000L), result)
    }

    @Test
    fun `speech segments match with their own length`() {
        val result = VideoMatches.findRanges(
            frames(0L to "intro"),
            "belasting",
            speech = listOf(TranscriptSegment(2500L, 7200L, "Belasting is niet verplicht"))
        )
        assertEquals(listOf(2500L until 7200L), result)
    }

    @Test
    fun `overlapping OCR and speech hits merge`() {
        val result = VideoMatches.findRanges(
            frames(3000L to "SALE", 9000L to "SALE"),
            "sale",
            speech = listOf(TranscriptSegment(1000L, 3500L, "big sale today"))
        )
        assertEquals(listOf(1000L until 4000L, 9000L until 10000L), result)
    }

    @Test
    fun `whole-query hit in speech beats single-word hits on screen`() {
        val result = VideoMatches.findRanges(
            frames(0L to "foo", 1000L to "bar"),
            "foo bar",
            speech = listOf(TranscriptSegment(5000L, 6000L, "and then foo bar"))
        )
        assertEquals(listOf(5000L until 6000L), result)
    }
}
