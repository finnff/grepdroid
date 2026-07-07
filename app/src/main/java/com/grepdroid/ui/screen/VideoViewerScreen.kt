package com.grepdroid.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.grepdroid.GrepdroidApp
import com.grepdroid.data.VideoProcessor
import com.grepdroid.data.db.ScannedVideo
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

    val jumpTimestampMs = remember(video, searchQuery) {
        if (video == null) 0L else findFirstMatchingFrameMs(video, searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
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
    ) { padding ->
        if (video != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Fall back to the contentUri when the thumbnail file is gone
                // (same eviction-resilience as the grid card).
                val thumbModel: Any = remember(video.mediaStoreId, video.thumbnailPath) {
                    val thumbFile = video.thumbnailPath.takeIf { it.isNotBlank() }?.let(::File)
                    if (thumbFile != null && thumbFile.exists()) thumbFile else video.contentUri
                }
                AsyncImage(
                    model = thumbModel,
                    contentDescription = video.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val saved = prefs.defaultVideoPlayerPackage
                        if (saved != null) {
                            val extras = if (jumpTimestampMs > 0) mapOf("position" to jumpTimestampMs.toInt()) else emptyMap()
                            val launched = tryLaunch(context, video.contentUri, "video/*", saved, extras)
                            if (!launched) {
                                prefs.defaultVideoPlayerPackage = null
                                showChooser = true
                            }
                        } else {
                            showChooser = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open at ${formatTimestamp(jumpTimestampMs)}")
                }

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
                    text = "Duration: ${formatTimestamp(video.durationMs)}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

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
                val extras = if (jumpTimestampMs > 0) mapOf("position" to jumpTimestampMs.toInt()) else emptyMap()
                tryLaunch(context, video.contentUri, "video/*", pkg, extras)
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

private fun findFirstMatchingFrameMs(video: ScannedVideo, query: String): Long {
    if (query.isBlank()) return 0L
    val q = query.lowercase()
    val frames = VideoProcessor.parseFrames(video.frameTimestampsJson)
    val hit = frames.firstOrNull { it.text.lowercase().contains(q) }
    if (hit != null) return hit.timestampMs

    val queryWords = q.split("\\s+".toRegex()).filter { it.isNotBlank() }
    val anyWord = frames.firstOrNull { frame ->
        val ftext = frame.text.lowercase()
        queryWords.any { ftext.contains(it) }
    }
    return anyWord?.timestampMs ?: 0L
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

private fun formatTimestamp(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
