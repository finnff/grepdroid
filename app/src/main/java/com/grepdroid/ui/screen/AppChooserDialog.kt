package com.grepdroid.ui.screen

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@Composable
fun AppChooserDialog(
    intent: Intent,
    title: String,
    currentDefault: String?,
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String, remember: Boolean) -> Unit,
    onClearDefault: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember(intent.action, intent.type, intent.dataString) {
        val pm = context.packageManager
        pm.queryIntentActivities(intent, 0).map { ri ->
            AppEntry(
                packageName = ri.activityInfo.packageName,
                label = ri.loadLabel(pm).toString(),
                icon = try {
                    pm.getApplicationIcon(ri.activityInfo.packageName)
                } catch (_: Exception) {
                    null
                }
            )
        }.sortedBy { it.label.lowercase() }
    }
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (apps.isEmpty()) {
                    Text("No app available")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(apps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAppSelected(app.packageName, rememberChoice)
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    AsyncImage(
                                        model = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(36.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, fontSize = 15.sp)
                                    if (app.packageName == currentDefault) {
                                        Text(
                                            "Current default",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Always use this app", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            if (currentDefault != null) {
                TextButton(onClick = onClearDefault) { Text("Clear default") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
