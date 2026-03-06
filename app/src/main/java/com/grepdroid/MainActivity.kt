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
            viewModel<MainViewModel>().startScan()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
    
    @Composable
    fun GrepdroidAppContent() {
        val app = remember { application as GrepdroidApp }
        val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app.repository))
        
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
                    }
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
    private val repository: com.grepdroid.repository.ImageRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return com.grepdroid.ui.MainViewModel(repository) as T
    }
}
