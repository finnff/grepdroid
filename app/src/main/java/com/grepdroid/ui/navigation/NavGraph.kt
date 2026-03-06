package com.grepdroid.ui.navigation

sealed class Screen(val route: String) {
    object Search : Screen("search")
    object ImageViewer : Screen("viewer/{mediaStoreId}") {
        fun createRoute(mediaStoreId: Long) = "viewer/$mediaStoreId"
    }
}

fun getScreenRoute(screen: Screen): String = screen.route
