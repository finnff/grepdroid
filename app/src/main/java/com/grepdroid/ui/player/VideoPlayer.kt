package com.grepdroid.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val SEEK_INCREMENT_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val SEEK_FEEDBACK_MS = 700L
private const val DEFAULT_ASPECT_RATIO = 16f / 9f
/** Inline (non-fullscreen) player never takes more than this share of the available height. */
private const val MAX_INLINE_HEIGHT_FRACTION = 0.6f
private val PLAYBACK_SPEEDS = listOf(1f, 1.5f, 2f, 0.5f)
/** A bit under the 48dp default so the bottom bar clears the center buttons on short inline players. */
private val BottomButtonSize = 40.dp

/** Playback settings that outlive a single [ExoPlayer] instance (see [rememberVideoPlayer]). */
private class PlaybackSnapshot(var positionMs: Long) {
    var playWhenReady = true
    var volume = 1f
    var speed = 1f
}

/**
 * Creates an [ExoPlayer] for [contentUri] while the screen is started and releases it
 * when stopped, so a backgrounded app doesn't hold on to hardware decoders. Position,
 * volume and speed carry over; after coming back the video waits paused.
 *
 * Returns null while there's no live player (before the first start, or while stopped).
 */
@Composable
fun rememberVideoPlayer(contentUri: String, startPositionMs: Long): ExoPlayer? {
    val context = LocalContext.current
    val snapshot = remember(contentUri) { PlaybackSnapshot(startPositionMs) }
    var player by remember(contentUri) { mutableStateOf<ExoPlayer?>(null) }

    LifecycleStartEffect(contentUri) {
        val exo = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
        exo.setMediaItem(MediaItem.fromUri(contentUri), snapshot.positionMs)
        exo.volume = snapshot.volume
        exo.setPlaybackSpeed(snapshot.speed)
        exo.playWhenReady = snapshot.playWhenReady
        exo.prepare()
        player = exo

        onStopOrDispose {
            snapshot.positionMs = exo.currentPosition
            snapshot.playWhenReady = false
            snapshot.volume = exo.volume
            snapshot.speed = exo.playbackParameters.speed
            player = null
            exo.release()
        }
    }
    return player
}

/** Snapshot of the [Player] fields the controls render, refreshed on every player event. */
@Stable
private class PlayerUiState {
    var playbackState by mutableIntStateOf(Player.STATE_IDLE)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var playWhenReady by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var error by mutableStateOf<PlaybackException?>(null)
        private set
    /** Width / height of the decoded video; null until the player reports a size. */
    var aspectRatio by mutableStateOf<Float?>(null)
        private set

    val showPlay: Boolean
        get() = !playWhenReady ||
            playbackState == Player.STATE_IDLE ||
            playbackState == Player.STATE_ENDED

    fun update(player: Player) {
        playbackState = player.playbackState
        isPlaying = player.isPlaying
        playWhenReady = player.playWhenReady
        isMuted = player.volume == 0f
        speed = player.playbackParameters.speed
        error = player.playerError
        val size = player.videoSize
        // Keep the last known ratio when a rebuilt player hasn't decoded a frame yet.
        if (size.width > 0 && size.height > 0) {
            aspectRatio = size.width * size.pixelWidthHeightRatio / size.height
        }
    }
}

private data class SeekFeedback(val forward: Boolean, val seconds: Long, val nonce: Int)

/**
 * In-app video player: video surface, auto-hiding controls, a seek bar with the search
 * hits highlighted, double-tap to skip ±10s, and a fullscreen mode that hides the
 * system bars and turns landscape clips sideways.
 *
 * Inline it sizes itself to the video's aspect ratio at full width; in fullscreen it
 * fills whatever it's given.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    player: ExoPlayer?,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    fallbackDurationMs: Long = 0L,
    matchRanges: List<LongRange> = emptyList(),
    placeholder: Any? = null,
    onOpenExternally: (() -> Unit)? = null
) {
    val ui = remember { PlayerUiState() }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        ui.update(player)
        val listener = object : Player.Listener {
            override fun onEvents(changed: Player, events: Player.Events) {
                ui.update(changed)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Continuous ticks feed the seek bar (read at draw time only); whole-second ticks feed the clock.
    val progress = rememberProgressStateWithTickInterval(player)
    val clock = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1000)
    val durationMs = clock.durationMs.takeIf { it > 0L } ?: fallbackDurationMs

    var controlsVisible by remember { mutableStateOf(true) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }
    // Bumped on every control interaction to restart the auto-hide countdown.
    var interactions by remember { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    val isScrubbing = scrubPositionMs != null

    LaunchedEffect(controlsVisible, ui.isPlaying, isScrubbing, interactions) {
        if (controlsVisible && ui.isPlaying && !isScrubbing) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(ui.playbackState) {
        if (ui.playbackState == Player.STATE_ENDED) controlsVisible = true
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(SEEK_FEEDBACK_MS)
            seekFeedback = null
        }
    }

    val view = LocalView.current
    DisposableEffect(view, ui.isPlaying) {
        view.keepScreenOn = ui.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    val currentOnFullscreenChange by rememberUpdatedState(onFullscreenChange)
    FullscreenEffect(enabled = isFullscreen, lockLandscape = (ui.aspectRatio ?: 0f) > 1f)
    BackHandler(enabled = isFullscreen) { currentOnFullscreenChange(false) }
    // Turning a phone sideways goes fullscreen, like most video apps. Tablets have room inline.
    val configuration = LocalConfiguration.current
    val isPhoneLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenHeightDp < 600
    LaunchedEffect(isPhoneLandscape) {
        if (isPhoneLandscape) currentOnFullscreenChange(true)
    }

    val sizeModifier = if (isFullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.inlineVideoSize(ui.aspectRatio ?: DEFAULT_ASPECT_RATIO)
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .background(Color.Black)
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        player?.let { p ->
                            val forward = offset.x > size.width / 2f
                            if (forward) p.seekForward() else p.seekBack()
                            val previous = seekFeedback
                            val step = SEEK_INCREMENT_MS / 1000
                            val seconds = if (previous != null && previous.forward == forward) previous.seconds + step else step
                            seekFeedback = SeekFeedback(forward, seconds, (previous?.nonce ?: 0) + 1)
                        }
                    }
                )
            }
    ) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            shutter = {
                // Until the first frame renders, show the indexed thumbnail instead of black.
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (placeholder != null) {
                        AsyncImage(
                            model = placeholder,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        )

        seekFeedback?.let { feedback ->
            SeekFeedbackBubble(
                feedback = feedback,
                modifier = Modifier
                    .align(if (feedback.forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 32.dp)
            )
        }

        val isBuffering = ui.playbackState == Player.STATE_BUFFERING && !isScrubbing
        if (isBuffering && !controlsVisible) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && ui.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.3f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
                    .then(
                        if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier
                    )
            ) {
                if (isFullscreen) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onFullscreenChange(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit fullscreen", tint = Color.White)
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (onOpenExternally != null) {
                            IconButton(onClick = onOpenExternally) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in another app", tint = Color.White)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player?.seekBack(); interactions++ }) {
                        Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        if (isBuffering) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            IconButton(
                                onClick = { player?.togglePlayPause(); interactions++ },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = when {
                                        ui.playbackState == Player.STATE_ENDED -> Icons.Filled.Replay
                                        ui.showPlay -> Icons.Filled.PlayArrow
                                        else -> Icons.Filled.Pause
                                    },
                                    contentDescription = when {
                                        ui.playbackState == Player.STATE_ENDED -> "Replay"
                                        ui.showPlay -> "Play"
                                        else -> "Pause"
                                    },
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { player?.seekForward(); interactions++ }) {
                        Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatPlaybackTime(scrubPositionMs ?: clock.currentPositionMs)} / ${formatPlaybackTime(durationMs)}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            player?.let { p ->
                                val next = PLAYBACK_SPEEDS[(PLAYBACK_SPEEDS.indexOf(ui.speed) + 1) % PLAYBACK_SPEEDS.size]
                                p.setPlaybackSpeed(next)
                            }
                            interactions++
                        }) {
                            Text(formatPlaybackSpeed(ui.speed), color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        IconButton(
                            onClick = {
                                player?.let { p -> p.volume = if (ui.isMuted) 1f else 0f }
                                interactions++
                            },
                            modifier = Modifier.size(BottomButtonSize)
                        ) {
                            Icon(
                                imageVector = if (ui.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (ui.isMuted) "Unmute" else "Mute",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { onFullscreenChange(!isFullscreen) },
                            modifier = Modifier.size(BottomButtonSize)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                    SeekBar(
                        positionMs = { scrubPositionMs ?: progress.currentPositionMs },
                        durationMs = durationMs,
                        matchRanges = matchRanges,
                        onScrubStart = { player?.setScrubbingModeEnabled(true) },
                        onScrub = { ms ->
                            scrubPositionMs = ms
                            player?.seekTo(ms)
                        },
                        onScrubEnd = { ms ->
                            player?.seekTo(ms)
                            player?.setScrubbingModeEnabled(false)
                            scrubPositionMs = null
                            interactions++
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        ui.error?.let {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Can't play this video", color = Color.White)
                Row {
                    TextButton(onClick = { player?.prepare() }) { Text("Retry") }
                    if (onOpenExternally != null) {
                        TextButton(onClick = onOpenExternally) { Text("Open in another app") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekFeedbackBubble(feedback: SeekFeedback, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (feedback.forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
            contentDescription = null,
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (feedback.forward) "+${feedback.seconds}s" else "-${feedback.seconds}s",
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Hides the system bars while [enabled], and for landscape clips also rotates the
 * screen sideways. Everything is restored when fullscreen ends or the player leaves.
 */
@Composable
private fun FullscreenEffect(enabled: Boolean, lockLandscape: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, lockLandscape) {
        val activity = context.findActivity()
        if (!enabled || activity == null) return@DisposableEffect onDispose { }

        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        val previousOrientation = activity.requestedOrientation
        if (lockLandscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previousOrientation
        }
    }
}

/** Full width at [aspectRatio], capped so tall clips leave room for the details below. */
private fun Modifier.inlineVideoSize(aspectRatio: Float): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val maxHeight = if (constraints.hasBoundedHeight) {
        (constraints.maxHeight * MAX_INLINE_HEIGHT_FRACTION).roundToInt()
    } else {
        Int.MAX_VALUE
    }
    val height = (width / aspectRatio).roundToInt().coerceAtMost(maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

private fun Player.togglePlayPause() {
    when {
        playbackState == Player.STATE_IDLE -> {
            prepare()
            play()
        }
        playbackState == Player.STATE_ENDED -> {
            seekToDefaultPosition()
            play()
        }
        playWhenReady -> pause()
        else -> play()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
