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
