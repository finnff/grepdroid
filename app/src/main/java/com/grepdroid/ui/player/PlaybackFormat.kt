package com.grepdroid.ui.player

/** `m:ss`, or `h:mm:ss` once the clip passes an hour. Negative / unset times read as 0:00. */
internal fun formatPlaybackTime(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** `1x`, `1.5x`, `0.5x` — drops the trailing `.0` on whole-number speeds. */
internal fun formatPlaybackSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
