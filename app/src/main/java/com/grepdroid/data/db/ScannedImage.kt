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

// FTS4 virtual table for full-text search on extracted_text
// FTS tables use rowid as implicit primary key, but Room requires explicit @PrimaryKey
@Entity(tableName = "scanned_images_fts")
data class ScannedImageFts(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    @ColumnInfo(name = "mediaStoreId") val mediaStoreId: Long,
    @ColumnInfo(name = "extracted_text") val extracted_text: String
)
