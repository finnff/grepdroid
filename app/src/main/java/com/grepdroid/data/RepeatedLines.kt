package com.grepdroid.data

/**
 * Collapses runs of repeated lines in OCR text.
 *
 * A block of one or more lines that immediately repeats the lines just before it is
 * dropped, so "A B C B C B C D" becomes "A B C D". This mostly matters for videos,
 * where a caption that stays on screen is OCR'd once per sampled frame. Blank lines
 * are dropped too, since frames with no text would otherwise split up the repeats.
 */
object RepeatedLines {

    /** Longest repeating block looked for; keeps long texts from going quadratic. */
    private const val MAX_BLOCK_LINES = 100

    fun collapse(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val repeat = (1..minOf(out.size, lines.size - i, MAX_BLOCK_LINES)).firstOrNull { k ->
                (0 until k).all { j -> out[out.size - k + j] == lines[i + j] }
            }
            if (repeat != null) {
                i += repeat
            } else {
                out.add(lines[i])
                i++
            }
        }
        return out.joinToString("\n")
    }
}
