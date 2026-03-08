package com.grepdroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class TrackedImage(
    val mediaStoreId: Long,
    val dateModified: Long
)

@Dao
interface ImageDao {
    
    @Query("SELECT scanned_images.* FROM scanned_images JOIN scanned_images_fts ON scanned_images.rowid = scanned_images_fts.rowid WHERE scanned_images_fts MATCH :query || '*' LIMIT 50")
    fun search(query: String): Flow<List<ScannedImage>>
    
    @Query("SELECT mediaStoreId, dateModified FROM scanned_images")
    fun getAllTracked(): Flow<List<TrackedImage>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: ScannedImage)
    
    @Query("DELETE FROM scanned_images WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM scanned_images WHERE mediaStoreId NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<Long>)
    
    @Query("SELECT * FROM scanned_images WHERE mediaStoreId = :mediaStoreId")
    suspend fun getById(mediaStoreId: Long): ScannedImage?

    @Query("SELECT * FROM scanned_images WHERE extractedText != '' ORDER BY scannedAt DESC LIMIT 1000")
    fun getAllWithText(): Flow<List<ScannedImage>>

    @Query("SELECT COUNT(*) FROM scanned_images")
    fun getTotalCount(): Flow<Int>
}
