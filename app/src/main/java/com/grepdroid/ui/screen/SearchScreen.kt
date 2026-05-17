package com.grepdroid.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.grepdroid.ui.MainViewModel
import com.grepdroid.ui.MediaFilter
import com.grepdroid.ui.MediaResult
import com.grepdroid.ui.SortOrder
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToImage: (Long) -> Unit,
    onNavigateToVideo: (Long) -> Unit,
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                        IconButton(onClick = { viewModel.startScan() }) {
                            Icon(Icons.Default.Refresh, "Scan gallery")
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
                val thumbModel: Any = when (result) {
                    is MediaResult.Image -> result.contentUri
                    is MediaResult.Video -> if (result.thumbnailPath.isNotBlank()) File(result.thumbnailPath) else result.contentUri
                }
                AsyncImage(
                    model = thumbModel,
                    contentDescription = result.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentScale = ContentScale.Crop
                )
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
