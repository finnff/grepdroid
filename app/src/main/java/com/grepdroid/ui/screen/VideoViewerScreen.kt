package com.grepdroid.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepdroid.GrepdroidApp
import com.grepdroid.data.VideoMatches
import com.grepdroid.data.VideoProcessor
import com.grepdroid.data.db.ScannedVideo
import com.grepdroid.ui.player.VideoPlayer
import com.grepdroid.ui.player.formatPlaybackTime
import com.grepdroid.ui.player.rememberVideoPlayer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoViewerScreen(
    mediaStoreId: Long,
    searchQuery: String,
    onBack: () -> Unit,
    video: ScannedVideo?
) {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as GrepdroidApp).userPreferences }
    var showChooser by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    val detailsScrollState = rememberScrollState()

    val matchRanges = remember(video, searchQuery) {
        if (video == null) emptyList()
        else VideoMatches.findRanges(VideoProcessor.parseFrames(video.frameTimestampsJson), searchQuery)
    }
    // Start on the first frame that matched the search, like the old "Open at MM:SS" did.
    val player = video?.let {
        rememberVideoPlayer(it.contentUri, startPositionMs = matchRanges.firstOrNull()?.first ?: 0L)
    }

    // Hands the clip to another app, picking up from wherever the in-app player is.
    fun launchExternally(packageName: String): Boolean {
        video ?: return false
        val positionMs = player?.currentPosition ?: 0L
        val extras = if (positionMs > 0) mapOf("position" to positionMs.toInt()) else emptyMap()
        return tryLaunch(context, video.contentUri, "video/*", packageName, extras)
    }

    val openExternally: () -> Unit = {
        player?.pause()
        val saved = prefs.defaultVideoPlayerPackage
        if (saved == null) {
            showChooser = true
        } else if (!launchExternally(saved)) {
            prefs.defaultVideoPlayerPackage = null
            showChooser = true
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Text("Video Details", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (video != null) openExternally() }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in another app")
                        }
                        IconButton(onClick = {
                            video?.extractedText?.let { text ->
                                if (text.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Extracted text", text))
                                    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy text")
                        }
                        IconButton(onClick = { shareVideo(context, video) }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                    }
                )
            }
        },
        containerColor = if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background,
        // Fullscreen draws under the (hidden) system bars; the player pads its own controls.
        contentWindowInsets = if (isFullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
    ) { padding ->
        if (video != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Shown until the first video frame renders. Falls back to the contentUri when
                // the thumbnail file is gone (same eviction-resilience as the grid card).
                val thumbModel: Any = remember(video.mediaStoreId, video.thumbnailPath) {
                    val thumbFile = video.thumbnailPath.takeIf { it.isNotBlank() }?.let(::File)
                    if (thumbFile != null && thumbFile.exists()) thumbFile else video.contentUri
                }
                VideoPlayer(
                    player = player,
                    isFullscreen = isFullscreen,
                    onFullscreenChange = { isFullscreen = it },
                    title = video.displayName,
                    fallbackDurationMs = video.durationMs,
                    matchRanges = matchRanges,
                    placeholder = thumbModel,
                    onOpenExternally = openExternally
                )

                if (!isFullscreen) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(detailsScrollState)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Name",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Text(
                            text = video.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Duration: ${formatPlaybackTime(video.durationMs)}",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        if (matchRanges.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Matches",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(matchRanges) { range ->
                                    AssistChip(
                                        onClick = {
                                            player?.seekTo(range.first)
                                            player?.play()
                                        },
                                        label = { Text(formatPlaybackTime(range.first)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Extracted Text",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Text(
                            text = video.extractedText,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Video not found")
            }
        }
    }

    if (showChooser && video != null) {
        val intent = buildViewIntent(video.contentUri, "video/*")
        AppChooserDialog(
            intent = intent,
            title = "Open video with",
            currentDefault = prefs.defaultVideoPlayerPackage,
            onDismiss = { showChooser = false },
            onAppSelected = { pkg, remember ->
                if (remember) prefs.defaultVideoPlayerPackage = pkg
                launchExternally(pkg)
                showChooser = false
            },
            onClearDefault = {
                prefs.defaultVideoPlayerPackage = null
                showChooser = false
                Toast.makeText(context, "Default cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun shareVideo(context: Context, video: ScannedVideo?) {
    video ?: return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(video.contentUri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share video"))
}
