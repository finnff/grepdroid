package com.grepdroid.data.db

import androidx.room.*

@Entity(tableName = "scanned_images")
data class ScannedImage(
    @PrimaryKey
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val dateModified: Long,
    val extractedText: String,
    val scannedAt: Long
)

@Fts4(contentEntity = ScannedImage::class)
@Entity(tableName = "scanned_images_fts")
data class ScannedImageFts(
    val extractedText: String
)

@Entity(tableName = "scanned_videos")
data class ScannedVideo(
    @PrimaryKey
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val durationMs: Long,
    val dateModified: Long,
    val extractedText: String,
    val frameTimestampsJson: String,
    val thumbnailPath: String,
    val scannedAt: Long
)

@Fts4(contentEntity = ScannedVideo::class)
@Entity(tableName = "scanned_videos_fts")
data class ScannedVideoFts(
    val extractedText: String
)
