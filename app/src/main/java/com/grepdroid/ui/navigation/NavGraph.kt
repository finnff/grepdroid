package com.grepdroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grepdroid.ui.screen.ImageViewerScreen
import com.grepdroid.ui.screen.SearchScreen

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.getScreenRoute(Screen.Search)
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.getScreenRoute(Screen.Search)) {
            SearchScreen(
                onNavigateToViewer = { id ->
                    navController.navigate(Screen.ImageViewer.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(
                navArgument("mediaStoreId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val mediaStoreId = backStackEntry.arguments?.getLong("mediaStoreId")
            // Note: Image data loading should be handled by the parent activity
            ImageViewerScreen(
                mediaStoreId = mediaStoreId ?: 0L,
                onBack = { navController.popBackStack() },
                image = null // Data passed from parent
            )
        }
    }
}