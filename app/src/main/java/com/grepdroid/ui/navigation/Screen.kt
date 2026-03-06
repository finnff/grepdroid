package com.grepdroid.ui.navigation

sealed class Screen(val route: String) {
    object Search : Screen("search")
    object ImageViewer : Screen("image_viewer/{mediaStoreId}") {
        fun createRoute(mediaStoreId: Long) = "image_viewer/$mediaStoreId"
    }

    companion object {
        fun getScreenRoute(screen: Screen) = screen.route
    }
}

fun getScreenRoute(screen: Screen) = screen.route