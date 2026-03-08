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
    onToggleBucket: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                    .heightIn(max = 400.dp)
            ) {
                // "All folders" option
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
                        Text("All folders", fontWeight = FontWeight.Medium)
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
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text("Confirm")
            }
        }
    }
}
