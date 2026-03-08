package com.grepdroid

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
import com.grepdroid.ui.theme.GrepdroidTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
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
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
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

        LaunchedEffect(selectedImageId) {
            if (selectedImageId != null) {
                selectedImage = app.repository.getScannedImage(selectedImageId!!)
            }
        }

        when (currentRoute) {
            getScreenRoute(Screen.Search) -> {
                SearchScreen(
                    onNavigateToViewer = { id ->
                        selectedImageId = id
                        currentRoute = Screen.ImageViewer.createRoute(id)
                    },
                    viewModel = viewModel
                )
            }
            else -> {
                if (selectedImageId != null) {
                    ImageViewerScreen(
                        mediaStoreId = selectedImageId!!,
                        onBack = {
                            currentRoute = getScreenRoute(Screen.Search)
                            selectedImageId = null
                            selectedImage = null
                        },
                        image = selectedImage
                    )
                }
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
