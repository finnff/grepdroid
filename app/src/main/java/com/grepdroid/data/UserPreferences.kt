package com.grepdroid.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("grepdroid_prefs", Context.MODE_PRIVATE)

    var selectedBucketIds: Set<Long>
        get() = prefs.getStringSet(KEY_BUCKET_IDS, null)
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        set(value) {
            prefs.edit()
                .putStringSet(KEY_BUCKET_IDS, value.map { it.toString() }.toSet())
                .apply()
        }

    var useFuzzySearch: Boolean
        get() = prefs.getBoolean(KEY_FUZZY_SEARCH, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FUZZY_SEARCH, value).apply()
        }

    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 3)
        set(value) {
            prefs.edit().putInt(KEY_GRID_COLUMNS, value).apply()
        }

    var sortOrder: String
        get() = prefs.getString(KEY_SORT_ORDER, "BEST_MATCH") ?: "BEST_MATCH"
        set(value) {
            prefs.edit().putString(KEY_SORT_ORDER, value).apply()
        }

    var indexVideos: Boolean
        get() = prefs.getBoolean(KEY_INDEX_VIDEOS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INDEX_VIDEOS, value).apply()
        }

    var selectedVideoBucketIds: Set<Long>
        get() = prefs.getStringSet(KEY_VIDEO_BUCKET_IDS, null)
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        set(value) {
            prefs.edit()
                .putStringSet(KEY_VIDEO_BUCKET_IDS, value.map { it.toString() }.toSet())
                .apply()
        }

    var defaultGalleryPackage: String?
        get() = prefs.getString(KEY_DEFAULT_GALLERY_PKG, null)
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_GALLERY_PKG, value).apply()
        }

    var defaultVideoPlayerPackage: String?
        get() = prefs.getString(KEY_DEFAULT_VIDEO_PLAYER_PKG, null)
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_VIDEO_PLAYER_PKG, value).apply()
        }

    var mediaFilter: String
        get() = prefs.getString(KEY_MEDIA_FILTER, "BOTH") ?: "BOTH"
        set(value) {
            prefs.edit().putString(KEY_MEDIA_FILTER, value).apply()
        }

    companion object {
        private const val KEY_BUCKET_IDS = "selected_bucket_ids"
        private const val KEY_FUZZY_SEARCH = "use_fuzzy_search"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_INDEX_VIDEOS = "index_videos"
        private const val KEY_VIDEO_BUCKET_IDS = "selected_video_bucket_ids"
        private const val KEY_DEFAULT_GALLERY_PKG = "default_gallery_pkg"
        private const val KEY_DEFAULT_VIDEO_PLAYER_PKG = "default_video_player_pkg"
        private const val KEY_MEDIA_FILTER = "media_filter"
    }
}
