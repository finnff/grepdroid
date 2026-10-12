package com.grepdroid.data

/**
 * Locates a search query inside a video's per-frame OCR text.
 *
 * Frames whose text contains the whole query win; if none do, frames containing
 * any single query word are used instead. Consecutive hits are merged into one
 * range that runs until the next sampled frame, so a caption that stays on
 * screen for 5s becomes a single 5s range rather than five 1s blips.
 */
object VideoMatches {

    fun findRanges(
        frames: List<FrameOcr>,
        query: String,
        frameIntervalMs: Long = VideoProcessor.DEFAULT_FRAME_INTERVAL_MS
    ): List<LongRange> {
        val hits = matchingTimestamps(frames, query)
        if (hits.isEmpty()) return emptyList()

        val ranges = mutableListOf<LongRange>()
        var start = hits.first()
        var end = start + frameIntervalMs
        for (t in hits.drop(1)) {
            if (t <= end) {
                end = t + frameIntervalMs
            } else {
                ranges.add(start until end)
                start = t
                end = t + frameIntervalMs
            }
        }
        ranges.add(start until end)
        return ranges
    }

    private fun matchingTimestamps(frames: List<FrameOcr>, query: String): List<Long> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val exact = frames.filter { it.text.lowercase().contains(q) }
        if (exact.isNotEmpty()) return exact.map { it.timestampMs }.distinct().sorted()

        val words = q.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return frames
            .filter { frame ->
                val text = frame.text.lowercase()
                words.any { text.contains(it) }
            }
            .map { it.timestampMs }
            .distinct()
            .sorted()
    }
}
