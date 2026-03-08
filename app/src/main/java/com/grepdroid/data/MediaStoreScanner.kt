package com.grepdroid.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MediaStoreScanner(private val context: Context) {

    data class ImageBucket(val bucketId: Long, val bucketName: String, val imageCount: Int)

    fun getAvailableBuckets(): List<ImageBucket> {
        val bucketMap = mutableMapOf<Long, Pair<String, Int>>()

        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.BUCKET_ID} IS NOT NULL",
            null,
            null
        )?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(bucketIdCol)
                val name = cursor.getString(bucketNameCol) ?: "Unknown"
                val existing = bucketMap[id]
                bucketMap[id] = if (existing != null) {
                    existing.first to (existing.second + 1)
                } else {
                    name to 1
                }
            }
        }

        return bucketMap.map { (id, pair) -> ImageBucket(id, pair.first, pair.second) }
            .sortedByDescending { it.imageCount }
    }

    fun scanImages(bucketIds: Set<Long>? = null): Flow<List<ImageItem>> = flow {
        val images = mutableListOf<ImageItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val selection = if (bucketIds != null && bucketIds.isNotEmpty()) {
            val placeholders = bucketIds.joinToString(",") { "?" }
            "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        } else {
            "${MediaStore.Images.Media.BUCKET_ID} IS NOT NULL"
        }

        val selectionArgs = if (bucketIds != null && bucketIds.isNotEmpty()) {
            bucketIds.map { it.toString() }.toTypedArray()
        } else {
            null
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateModified = cursor.getLong(dateColumn)

                images.add(
                    ImageItem(
                        mediaStoreId = id,
                        displayName = name,
                        dateModified = dateModified,
                        contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()
                    )
                )
            }
        }

        emit(images)
    }

    data class ImageItem(
        val mediaStoreId: Long,
        val displayName: String,
        val dateModified: Long,
        val contentUri: String
    )
}
