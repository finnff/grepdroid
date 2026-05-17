package com.grepdroid

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepdroid.ui.MainViewModel
import com.grepdroid.ui.navigation.Screen
import com.grepdroid.ui.navigation.getScreenRoute
import com.grepdroid.ui.screen.ImageViewerScreen
import com.grepdroid.ui.screen.SearchScreen
import com.grepdroid.ui.screen.VideoViewerScreen
import com.grepdroid.ui.theme.GrepdroidTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val anyGranted = permissions.values.any { it }
        if (anyGranted) {
            (application as GrepdroidApp).repository.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionsIfNeeded()

        setContent {
            GrepdroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GrepdroidAppContent()
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            (application as GrepdroidApp).repository.startScan()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    @Composable
    fun GrepdroidAppContent() {
        val app = remember { application as GrepdroidApp }
        val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app.repository, app.userPreferences))

        var currentRoute by remember { mutableStateOf(getScreenRoute(Screen.Search)) }
        var selectedImageId by remember { mutableStateOf<Long?>(null) }
        var selectedImage by remember { mutableStateOf<com.grepdroid.data.db.ScannedImage?>(null) }
        var selectedVideoId by remember { mutableStateOf<Long?>(null) }
        var selectedVideo by remember { mutableStateOf<com.grepdroid.data.db.ScannedVideo?>(null) }
        var searchQueryAtTap by remember { mutableStateOf("") }

        LaunchedEffect(selectedImageId) {
            if (selectedImageId != null) {
                selectedImage = app.repository.getScannedImage(selectedImageId!!)
            }
        }

        LaunchedEffect(selectedVideoId) {
            if (selectedVideoId != null) {
                selectedVideo = app.repository.getScannedVideo(selectedVideoId!!)
            }
        }

        when {
            currentRoute == getScreenRoute(Screen.Search) -> {
                SearchScreen(
                    onNavigateToImage = { id ->
                        searchQueryAtTap = viewModel.lastSearchQuery
                        selectedImageId = id
                        currentRoute = Screen.ImageViewer.createRoute(id)
                    },
                    onNavigateToVideo = { id ->
                        searchQueryAtTap = viewModel.lastSearchQuery
                        selectedVideoId = id
                        currentRoute = Screen.VideoViewer.createRoute(id)
                    },
                    viewModel = viewModel
                )
            }
            selectedImageId != null -> {
                val onBackToSearch = {
                    currentRoute = getScreenRoute(Screen.Search)
                    selectedImageId = null
                    selectedImage = null
                }
                BackHandler(onBack = onBackToSearch)
                ImageViewerScreen(
                    mediaStoreId = selectedImageId!!,
                    onBack = onBackToSearch,
                    image = selectedImage
                )
            }
            selectedVideoId != null -> {
                val onBackToSearch = {
                    currentRoute = getScreenRoute(Screen.Search)
                    selectedVideoId = null
                    selectedVideo = null
                }
                BackHandler(onBack = onBackToSearch)
                VideoViewerScreen(
                    mediaStoreId = selectedVideoId!!,
                    searchQuery = searchQueryAtTap,
                    onBack = onBackToSearch,
                    video = selectedVideo
                )
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: com.grepdroid.repository.ImageRepository,
    private val userPreferences: com.grepdroid.data.UserPreferences
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return com.grepdroid.ui.MainViewModel(repository, userPreferences) as T
    }
}
