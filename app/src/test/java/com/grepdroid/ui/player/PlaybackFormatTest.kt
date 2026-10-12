package com.grepdroid.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFormatTest {

    @Test
    fun `under an hour is m colon ss`() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("0:09", formatPlaybackTime(9_999L))
        assertEquals("12:34", formatPlaybackTime((12 * 60 + 34) * 1000L))
    }

    @Test
    fun `an hour or more adds the hour field`() {
        assertEquals("1:02:03", formatPlaybackTime((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun `negative or unset time reads as zero`() {
        assertEquals("0:00", formatPlaybackTime(-5_000L))
        assertEquals("0:00", formatPlaybackTime(Long.MIN_VALUE + 1))
    }

    @Test
    fun `whole speeds drop the decimal`() {
        assertEquals("1x", formatPlaybackSpeed(1f))
        assertEquals("2x", formatPlaybackSpeed(2f))
    }

    @Test
    fun `fractional speeds keep it`() {
        assertEquals("1.5x", formatPlaybackSpeed(1.5f))
        assertEquals("0.5x", formatPlaybackSpeed(0.5f))
    }
}
