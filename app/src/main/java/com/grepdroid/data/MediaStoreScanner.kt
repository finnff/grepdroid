package com.grepdroid.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MediaStoreScanner(private val context: Context) {
    
    fun scanImages(): Flow<List<ImageItem>> = flow {
        val images = mutableListOf<ImageItem>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA
        )
        
        val selection = "${MediaStore.Images.Media.BUCKET_ID} IS NOT NULL"
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateModified = cursor.getLong(dateColumn)
                val path = cursor.getString(3) ?: continue
                
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
