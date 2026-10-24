package com.grepdroid.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepdroid.data.MediaStoreScanner
import com.grepdroid.data.SpeechModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    buckets: List<MediaStoreScanner.ImageBucket>,
    selectedBucketIds: Set<Long>,
    videoBuckets: List<MediaStoreScanner.VideoBucket>,
    selectedVideoBucketIds: Set<Long>,
    indexVideos: Boolean,
    onToggleBucket: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onToggleVideoBucket: (Long) -> Unit,
    onSelectAllVideos: () -> Unit,
    onToggleIndexVideos: () -> Unit,
    transcribeSpeech: Boolean,
    speechModelState: SpeechModel.State,
    onToggleTranscribeSpeech: () -> Unit,
    onDownloadSpeechModel: () -> Unit,
    onCancelSpeechModelDownload: () -> Unit,
    onDeleteSpeechModel: () -> Unit,
    onConfirm: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onConfirm) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Select folders to scan",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 500.dp)
            ) {
                item {
                    Text(
                        "Images",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAll() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedBucketIds.isNotEmpty() && selectedBucketIds.size == buckets.size,
                            onCheckedChange = { onSelectAll() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All image folders", fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                }

                items(buckets) { bucket ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleBucket(bucket.bucketId) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = bucket.bucketId in selectedBucketIds,
                            onCheckedChange = { onToggleBucket(bucket.bucketId) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bucket.bucketName, fontWeight = FontWeight.Medium)
                            Text(
                                "${bucket.imageCount} images",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleIndexVideos() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = indexVideos,
                            onCheckedChange = { onToggleIndexVideos() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Index videos", fontWeight = FontWeight.Medium)
                            Text(
                                "Samples 1 frame/sec — slower than images",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (indexVideos) {
                    item {
                        TranscribeSpeechSetting(
                            enabled = transcribeSpeech,
                            modelState = speechModelState,
                            onToggle = onToggleTranscribeSpeech,
                            onDownload = onDownloadSpeechModel,
                            onCancelDownload = onCancelSpeechModelDownload,
                            onDelete = onDeleteSpeechModel
                        )
                        HorizontalDivider()
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAllVideos() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedVideoBucketIds.isNotEmpty() && selectedVideoBucketIds.size == videoBuckets.size,
                                onCheckedChange = { onSelectAllVideos() }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("All video folders", fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider()
                    }

                    items(videoBuckets) { bucket ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleVideoBucket(bucket.bucketId) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = bucket.bucketId in selectedVideoBucketIds,
                                onCheckedChange = { onToggleVideoBucket(bucket.bucketId) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bucket.bucketName, fontWeight = FontWeight.Medium)
                                Text(
                                    "${bucket.videoCount} videos",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * "Transcribe speech" switch plus the state of the speech model it needs: a
 * download button, download progress, or a way to delete the model again.
 */
@Composable
private fun TranscribeSpeechSetting(
    enabled: Boolean,
    modelState: SpeechModel.State,
    onToggle: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val totalMb = SpeechModel.TOTAL_BYTES / 1_000_000
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = enabled, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Transcribe speech", fontWeight = FontWeight.Medium)
            Text(
                "Makes spoken words searchable · English, Dutch, German, French, Spanish and more. " +
                    "Runs on-device after each scan; uses more threads while charging",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val status: String
    val action: Pair<String, () -> Unit>
    when (modelState) {
        SpeechModel.State.Missing -> {
            status = "Needs a one-time $totalMb MB download (Wi-Fi recommended)"
            action = "Download" to onDownload
        }
        is SpeechModel.State.Downloading -> {
            status = "Downloading ${modelState.downloadedBytes / 1_000_000} / $totalMb MB"
            action = "Cancel" to onCancelDownload
        }
        SpeechModel.State.Ready -> {
            status = "Speech model downloaded ($totalMb MB)"
            action = "Delete" to onDelete
        }
        is SpeechModel.State.Failed -> {
            status = "Download failed: ${modelState.message}"
            action = "Retry" to onDownload
        }
    }
    Column(modifier = Modifier.padding(start = 60.dp, bottom = 8.dp)) {
        if (modelState is SpeechModel.State.Downloading) {
            LinearProgressIndicator(
                progress = { modelState.downloadedBytes.toFloat() / modelState.totalBytes },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                status,
                fontSize = 12.sp,
                color = if (modelState is SpeechModel.State.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = action.second) { Text(action.first) }
        }
    }
}
