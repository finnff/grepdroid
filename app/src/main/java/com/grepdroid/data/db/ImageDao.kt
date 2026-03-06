package com.grepdroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class TrackedImage(
    val mediaStoreId: Long,
    val dateModified: Long
)

@Dao
interface ImageDao {
    
    @Query("SELECT * FROM scanned_images WHERE mediaStoreId IN (SELECT mediaStoreId FROM scanned_images_fts WHERE extracted_text MATCH :query || '*') LIMIT 50")
    fun search(query: String): Flow<List<ScannedImage>>
    
    @Query("SELECT mediaStoreId, dateModified FROM scanned_images")
    fun getAllTracked(): Flow<List<TrackedImage>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: ScannedImage)
    
    @Query("DELETE FROM scanned_images WHERE mediaStoreId NOT IN (:ids)")
    suspend fun deleteRemoved(ids: List<Long>)
    
    @Query("SELECT * FROM scanned_images WHERE mediaStoreId = :mediaStoreId")
    suspend fun getById(mediaStoreId: Long): ScannedImage?
}
