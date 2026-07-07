package com.grepdroid.ui.screen

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.grepdroid.data.VideoProcessor
import com.grepdroid.ui.MainViewModel
import com.grepdroid.ui.MediaFilter
import com.grepdroid.ui.MediaResult
import com.grepdroid.ui.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToImage: (Long) -> Unit,
    onNavigateToVideo: (Long) -> Unit,
    viewModel: MainViewModel,
    gridState: LazyGridState
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRegenDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Grepdroid", fontWeight = FontWeight.Bold)
                        val parts = buildList {
                            if (uiState.totalScannedCount > 0) add("${uiState.totalScannedCount} images")
                            if (uiState.totalScannedVideoCount > 0) add("${uiState.totalScannedVideoCount} videos")
                            val inSel = uiState.totalImagesInSelection + uiState.totalVideosInSelection
                            if (inSel > 0) add("$inSel in folders")
                        }
                        if (parts.isNotEmpty()) {
                            Text(
                                parts.joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    val (filterIcon, filterLabel) = when (uiState.mediaFilter) {
                        MediaFilter.BOTH -> Icons.Default.PhotoLibrary to "Both"
                        MediaFilter.IMAGES -> Icons.Default.Image to "Images"
                        MediaFilter.VIDEOS -> Icons.Default.Movie to "Videos"
                    }
                    IconButton(onClick = {
                        viewModel.cycleMediaFilter()
                        val next = when (uiState.mediaFilter) {
                            MediaFilter.BOTH -> "Showing images only"
                            MediaFilter.IMAGES -> "Showing videos only"
                            MediaFilter.VIDEOS -> "Showing images and videos"
                        }
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = next,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }) {
                        Icon(filterIcon, "Filter: $filterLabel")
                    }
                    IconButton(onClick = { viewModel.loadBuckets() }) {
                        Icon(Icons.Default.Folder, "Select folders")
                    }
                    if (uiState.isScanning) {
                        IconButton(onClick = { viewModel.cancelScan() }) {
                            Icon(Icons.Default.Cancel, "Cancel scan")
                        }
                    } else {
                        // Tap = scan; hold (>= REGEN_HOLD_MS) = repair missing thumbnails.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            // tryAwaitRelease() returns false when the
                                            // press is cancelled (e.g. dragged off) — treat
                                            // that as neither scan nor repair.
                                            val outcome = withTimeoutOrNull(REGEN_HOLD_MS) {
                                                tryAwaitRelease()
                                            }
                                            when (outcome) {
                                                null -> showRegenDialog = true
                                                true -> viewModel.startScan()
                                                false -> Unit
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, "Scan gallery (hold to repair thumbnails)")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search text in images & videos...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    singleLine = true
                )
                FilterChip(
                    selected = uiState.isFuzzySearch,
                    onClick = { viewModel.toggleFuzzySearch() },
                    label = { Text("Fuzzy") }
                )
            }

            if (uiState.isScanning) {
                val progress = uiState.scanProgress
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${progress.processed} / ${progress.total}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (progress.estimatedSecondsRemaining != null && progress.estimatedSecondsRemaining > 0) {
                        Text(
                            text = "~${formatEta(progress.estimatedSecondsRemaining)} remaining",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.searchQuery.isNotEmpty() && uiState.searchResults.isNotEmpty()) {
                val sortLabel = when (uiState.sortOrder) {
                    SortOrder.BEST_MATCH -> "Best"
                    SortOrder.ALPHABETICAL -> "A-Z"
                    SortOrder.NEWEST_FIRST -> "New"
                    SortOrder.OLDEST_FIRST -> "Old"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.searchResults.size} results",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { viewModel.cycleGridColumns() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.GridView, contentDescription = "Grid: ${uiState.gridColumns} columns", modifier = Modifier.size(16.dp))
                    }
                    Text("${uiState.gridColumns}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, end = 8.dp))
                    FilledTonalIconButton(onClick = { viewModel.cycleSortOrder() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort: $sortLabel", modifier = Modifier.size(16.dp))
                    }
                    Text(sortLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                }
            }

            if (uiState.searchResults.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No results found")
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(uiState.gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults, key = { "${it::class.simpleName}-${it.id}" }) { result ->
                        MediaCard(
                            result = result,
                            onClick = {
                                when (result) {
                                    is MediaResult.Image -> onNavigateToImage(result.id)
                                    is MediaResult.Video -> onNavigateToVideo(result.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showFolderPicker) {
        FolderPickerDialog(
            buckets = uiState.availableBuckets,
            selectedBucketIds = uiState.selectedBucketIds,
            videoBuckets = uiState.availableVideoBuckets,
            selectedVideoBucketIds = uiState.selectedVideoBucketIds,
            indexVideos = uiState.indexVideos,
            onToggleBucket = { viewModel.toggleBucket(it) },
            onSelectAll = { viewModel.selectAllBuckets() },
            onToggleVideoBucket = { viewModel.toggleVideoBucket(it) },
            onSelectAllVideos = { viewModel.selectAllVideoBuckets() },
            onToggleIndexVideos = { viewModel.toggleIndexVideos() },
            onConfirm = { viewModel.confirmBucketSelection() },
            onDismiss = { viewModel.dismissFolderPicker() }
        )
    }
    if (showRegenDialog) {
        AlertDialog(
            onDismissRequest = { showRegenDialog = false },
            title = { Text("Repair thumbnails") },
            text = {
                Text(
                    "Decode a fresh thumbnail for every video whose thumbnail " +
                        "file is missing? Runs in the background and shows " +
                        "progress in the scan bar. It does not re-run OCR."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRegenDialog = false
                    viewModel.regenerateMissingVideoThumbnails()
                }) { Text("Repair") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MediaCard(
    result: MediaResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column {
                if (result is MediaResult.Video) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        VideoThumbnail(video = result, onClick = onClick)
                    }
                } else {
                    AsyncImage(
                        model = result.contentUri,
                        contentDescription = result.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = result.displayName.removePrefix("Screenshot_").substringBeforeLast("."),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(4.dp)
                )
            }

            if (result is MediaResult.Video) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatDuration(result.durationMs),
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }

            if (result.matchPercent != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "${result.matchPercent}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Progress points (fraction of duration) sampled for the press-and-hold preview. */
private val VIDEO_PREVIEW_POINTS = listOf(0f, 0.25f, 0.5f, 0.75f)
private const val VIDEO_PREVIEW_FRAME_INTERVAL_MS = 250L // 4 fps
private const val VIDEO_PREVIEW_HOLD_THRESHOLD_MS = 300L
private const val REGEN_HOLD_MS = 3000L

/**
 * Static video thumbnail that, on press-and-hold, decodes a few frames spread
 * across the clip (see [VIDEO_PREVIEW_POINTS]) and cycles them at 4 fps. A quick
 * tap navigates to the viewer; only a sustained hold triggers the preview.
 */
@Composable
private fun VideoThumbnail(
    video: MediaResult.Video,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    var previewFrames by remember(video.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var frameIndex by remember(video.id) { mutableStateOf(0) }
    var previewActive by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(previewActive, video.id) {
        if (!previewActive) return@LaunchedEffect
        try {
            val frames = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(video.contentUri))
                    val duration = video.durationMs.takeIf { it > 0 } ?: 0L
                    if (duration > 0L) {
                        VIDEO_PREVIEW_POINTS.mapNotNull { progress ->
                            val timeUs = (duration * progress).toLong() * 1000L
                            VideoProcessor.decodePreviewFrame(retriever, timeUs)
                        }
                    } else {
                        emptyList<Bitmap>()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<Bitmap>()
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
            if (frames.isEmpty() || !isActive) return@LaunchedEffect
            previewFrames = frames
            frameIndex = 0
            while (isActive) {
                delay(VIDEO_PREVIEW_FRAME_INTERVAL_MS)
                frameIndex = (frameIndex + 1) % frames.size
            }
        } finally {
            previewFrames = emptyList()
            frameIndex = 0
        }
    }

    val currentFrame = previewFrames.getOrNull(frameIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(video.id) {
                detectTapGestures(
                    onPress = {
                        // tryAwaitRelease() returns true on a real lift, false when the
                        // press is cancelled (the grid took over to scroll), and times out
                        // (null) on a sustained hold. Only a genuine lift opens the viewer
                        // — a scroll-drag must never open it.
                        val outcome = withTimeoutOrNull(VIDEO_PREVIEW_HOLD_THRESHOLD_MS) {
                            tryAwaitRelease()
                        }
                        when (outcome) {
                            null -> {
                                previewActive = true
                                tryAwaitRelease()
                                previewActive = false
                            }
                            true -> onClick()
                            false -> Unit
                        }
                    }
                )
            }
    ) {
        if (currentFrame != null) {
            Image(
                bitmap = currentFrame.asImageBitmap(),
                contentDescription = video.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val staticModel: Any = remember(video.id, video.thumbnailPath) {
                // The OS may evict cacheDir, leaving the stored thumbnail path
                // dangling. Fall back to the contentUri so Coil renders the
                // first frame instead of a blank card (no rescan required).
                val thumbFile = video.thumbnailPath.takeIf { it.isNotBlank() }?.let(::File)
                if (thumbFile != null && thumbFile.exists()) thumbFile else video.contentUri
            }
            AsyncImage(
                model = staticModel,
                contentDescription = video.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun formatEta(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
