package com.grepdroid.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

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
data class ScannedImageFts(
    val mediaStoreId: Long,
    val extractedText: String
)
