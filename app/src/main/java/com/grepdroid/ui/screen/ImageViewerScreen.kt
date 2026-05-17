package com.grepdroid.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.grepdroid.GrepdroidApp
import com.grepdroid.data.db.ScannedImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    mediaStoreId: Long,
    onBack: () -> Unit,
    image: ScannedImage?
) {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as GrepdroidApp).userPreferences }
    var fullscreen by remember { mutableStateOf(false) }
    var showChooser by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (image == null) return@IconButton
                        val saved = prefs.defaultGalleryPackage
                        if (saved != null) {
                            val launched = tryLaunch(context, image.contentUri, "image/*", saved)
                            if (!launched) {
                                prefs.defaultGalleryPackage = null
                                showChooser = true
                            }
                        } else {
                            showChooser = true
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in gallery")
                    }
                    IconButton(onClick = {
                        image?.extractedText?.let { text ->
                            if (text.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Extracted text", text))
                                Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy text")
                    }
                    IconButton(onClick = { shareImage(context, image) }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                }
            )
        }
    ) { padding ->
        if (image != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                AsyncImage(
                    model = image.contentUri,
                    contentDescription = image.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clickable { fullscreen = true },
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Name",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = image.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
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
                    text = image.extractedText,
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
                Text("Image not found")
            }
        }
    }

    if (fullscreen && image != null) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreen = false }
            ) {
                AsyncImage(
                    model = image.contentUri,
                    contentDescription = image.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    }

    if (showChooser && image != null) {
        val intent = buildViewIntent(image.contentUri, "image/*")
        AppChooserDialog(
            intent = intent,
            title = "Open image with",
            currentDefault = prefs.defaultGalleryPackage,
            onDismiss = { showChooser = false },
            onAppSelected = { pkg, remember ->
                if (remember) prefs.defaultGalleryPackage = pkg
                tryLaunch(context, image.contentUri, "image/*", pkg)
                showChooser = false
            },
            onClearDefault = {
                prefs.defaultGalleryPackage = null
                showChooser = false
                Toast.makeText(context, "Default cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

internal fun buildViewIntent(uriString: String, mimeType: String): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uriString), mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

internal fun tryLaunch(context: Context, uriString: String, mimeType: String, packageName: String, extras: Map<String, Int> = emptyMap()): Boolean {
    val intent = buildViewIntent(uriString, mimeType).apply {
        setPackage(packageName)
        extras.forEach { (k, v) -> putExtra(k, v) }
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        try {
            // Fallback: try explicit component lookup
            val pm = context.packageManager
            val resolveIntent = buildViewIntent(uriString, mimeType)
            val match = pm.queryIntentActivities(resolveIntent, 0)
                .firstOrNull { it.activityInfo.packageName == packageName }
            if (match != null) {
                val explicit = buildViewIntent(uriString, mimeType).apply {
                    component = ComponentName(match.activityInfo.packageName, match.activityInfo.name)
                    extras.forEach { (k, v) -> putExtra(k, v) }
                }
                context.startActivity(explicit)
                true
            } else {
                Toast.makeText(context, "Default app no longer available", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}

private fun shareImage(context: Context, image: ScannedImage?) {
    image ?: return

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(image.contentUri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share image"))
}
