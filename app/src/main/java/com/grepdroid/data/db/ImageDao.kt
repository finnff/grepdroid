package com.grepdroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class TrackedImage(
    val mediaStoreId: Long,
    val dateModified: Long
)

@Dao
interface ImageDao {
    
    @Query("SELECT si.*, fts.rank FROM scanned_images si JOIN scanned_images_fts fts ON si.mediaStoreId = fts.mediaStoreId WHERE fts.extractedText MATCH :query* ORDER BY fts.rank LIMIT 50")
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
