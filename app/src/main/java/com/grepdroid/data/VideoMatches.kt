package com.grepdroid.data

/**
 * Locates a search query inside a video's per-frame OCR text and its speech
 * transcript.
 *
 * Frames/segments containing the whole query win; if none do, those containing
 * any single query word are used instead. Each OCR hit lasts until the next sampled
 * frame and each speech hit lasts as long as its segment; overlapping or touching
 * hits are merged into one range, so a caption that stays on screen for 5s becomes
 * a single 5s range rather than five 1s blips.
 */
object VideoMatches {

    fun findRanges(
        frames: List<FrameOcr>,
        query: String,
        frameIntervalMs: Long = VideoProcessor.DEFAULT_FRAME_INTERVAL_MS,
        speech: List<TranscriptSegment> = emptyList()
    ): List<LongRange> {
        val spans = frames.map { Span(it.timestampMs, it.timestampMs + frameIntervalMs, it.text) } +
            speech.map { Span(it.startMs, it.endMs, it.text) }
        val hits = matchingSpans(spans, query).sortedBy { it.start }
        if (hits.isEmpty()) return emptyList()

        val ranges = mutableListOf<LongRange>()
        var start = hits.first().start
        var end = hits.first().end
        for (hit in hits.drop(1)) {
            if (hit.start <= end) {
                end = maxOf(end, hit.end)
            } else {
                ranges.add(start until end)
                start = hit.start
                end = hit.end
            }
        }
        ranges.add(start until end)
        return ranges
    }

    private class Span(val start: Long, val end: Long, val text: String)

    private fun matchingSpans(spans: List<Span>, query: String): List<Span> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val exact = spans.filter { it.text.lowercase().contains(q) }
        if (exact.isNotEmpty()) return exact

        val words = q.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return spans.filter { span ->
            val text = span.text.lowercase()
            words.any { text.contains(it) }
        }
    }
}
