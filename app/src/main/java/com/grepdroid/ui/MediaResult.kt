package com.grepdroid.ui

import com.grepdroid.data.db.ScannedImage
import com.grepdroid.data.db.ScannedVideo

sealed class MediaResult {
    abstract val id: Long
    abstract val contentUri: String
    abstract val displayName: String
    abstract val dateModified: Long
    abstract val extractedText: String
    abstract val matchPercent: Int?

    data class Image(
        val image: ScannedImage,
        override val matchPercent: Int? = null
    ) : MediaResult() {
        override val id: Long get() = image.mediaStoreId
        override val contentUri: String get() = image.contentUri
        override val displayName: String get() = image.displayName
        override val dateModified: Long get() = image.dateModified
        override val extractedText: String get() = image.extractedText
    }

    data class Video(
        val video: ScannedVideo,
        override val matchPercent: Int? = null
    ) : MediaResult() {
        override val id: Long get() = video.mediaStoreId
        override val contentUri: String get() = video.contentUri
        override val displayName: String get() = video.displayName
        override val dateModified: Long get() = video.dateModified
        override val extractedText: String get() = video.extractedText
        val durationMs: Long get() = video.durationMs
        val thumbnailPath: String get() = video.thumbnailPath
    }
}
