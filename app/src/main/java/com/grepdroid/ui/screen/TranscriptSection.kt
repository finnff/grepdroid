package com.grepdroid.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepdroid.data.TranscriptSegment
import com.grepdroid.data.db.ScannedVideo
import com.grepdroid.ui.player.formatPlaybackTime

/**
 * What's said in the video, one line per stretch of speech. Tapping a line's
 * timestamp seeks the player there; the text itself can be long-pressed to select.
 * Shows nothing until the video has been transcribed.
 */
@Composable
internal fun TranscriptSection(
    transcribedAt: Long,
    segments: List<TranscriptSegment>,
    onSeek: (Long) -> Unit
) {
    if (transcribedAt == 0L) return
    Text(
        text = "Transcript",
        fontSize = 12.sp,
        color = Color.Gray,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    val emptyMessage = when {
        transcribedAt == ScannedVideo.TRANSCRIBE_FAILED -> "Couldn't read the audio"
        segments.isEmpty() -> "No speech detected"
        else -> null
    }
    if (emptyMessage != null) {
        Text(
            text = emptyMessage,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        for (segment in segments) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatPlaybackTime(segment.startMs),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onSeek(segment.startMs) }
                        .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                        .widthIn(min = 40.dp)
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = segment.text,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}
