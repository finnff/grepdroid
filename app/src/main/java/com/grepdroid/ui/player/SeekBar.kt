package com.grepdroid.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Amber "highlighter" used to mark where the search query appears in the clip. */
internal val MatchMarkerColor = Color(0xFFFFC107)

private val TrackHeight = 4.dp
private val MarkerHeight = 10.dp
private val MinMarkerWidth = 3.dp
private val ThumbRadius = 6.dp
private val ThumbRadiusDragging = 9.dp

/**
 * Video scrubber: tap anywhere to jump, drag to scrub. [matchRanges] are drawn as
 * highlighted blocks over the track so you can see where the searched text shows up.
 *
 * [positionMs] is a lambda so the playhead is only read at draw time; ticking
 * playback redraws the bar without recomposing anything.
 */
@Composable
internal fun SeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    matchRanges: List<LongRange>,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f),
    markerColor: Color = MatchMarkerColor
) {
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    var dragging by remember { mutableStateOf(false) }
    val thumbRadius by animateDpAsState(
        targetValue = if (dragging) ThumbRadiusDragging else ThumbRadius,
        label = "seekThumb"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0L) return@pointerInput
                // Same inset as the drawing below, so the thumb lands under the finger.
                val inset = ThumbRadiusDragging.toPx()
                fun positionAt(x: Float): Long {
                    val fraction = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    return (fraction * durationMs).toLong()
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Consume so the player's tap-to-toggle-controls doesn't also fire.
                    down.consume()
                    var target = positionAt(down.position.x)
                    dragging = true
                    currentOnScrubStart()
                    currentOnScrub(target)
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            target = positionAt(change.position.x)
                            currentOnScrub(target)
                        }
                    } finally {
                        dragging = false
                        currentOnScrubEnd(target)
                    }
                }
            }
            .semantics {
                contentDescription = "Seek bar"
                if (durationMs > 0L) {
                    setProgress { fraction ->
                        val target = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
                        currentOnScrubStart()
                        currentOnScrub(target)
                        currentOnScrubEnd(target)
                        true
                    }
                }
            }
    ) {
        val inset = ThumbRadiusDragging.toPx()
        val start = inset
        val trackWidth = (size.width - 2 * inset).coerceAtLeast(0f)
        val centerY = size.height / 2f
        val trackStroke = TrackHeight.toPx()

        drawLine(
            color = inactiveColor,
            start = Offset(start, centerY),
            end = Offset(start + trackWidth, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )

        val fraction = if (durationMs > 0L) {
            (positionMs().toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        val playheadX = start + trackWidth * fraction
        drawLine(
            color = activeColor,
            start = Offset(start, centerY),
            end = Offset(playheadX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )

        if (durationMs > 0L) {
            val markerHeight = MarkerHeight.toPx()
            val minMarkerWidth = MinMarkerWidth.toPx()
            for (range in matchRanges) {
                val x0 = start + trackWidth * (range.first.toFloat() / durationMs).coerceIn(0f, 1f)
                val x1 = start + trackWidth * ((range.last + 1).toFloat() / durationMs).coerceIn(0f, 1f)
                drawRoundRect(
                    color = markerColor,
                    topLeft = Offset(x0, centerY - markerHeight / 2f),
                    size = Size(max(x1 - x0, minMarkerWidth), markerHeight),
                    cornerRadius = CornerRadius(minMarkerWidth / 2f)
                )
            }
        }

        drawCircle(
            color = activeColor,
            radius = thumbRadius.toPx(),
            center = Offset(playheadX, centerY)
        )
    }
}
