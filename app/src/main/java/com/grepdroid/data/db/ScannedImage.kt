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
