package com.grepdroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class TrackedVideo(
    val mediaStoreId: Long,
    val dateModified: Long
)

@Dao
interface VideoDao {

    @Query("SELECT scanned_videos.* FROM scanned_videos JOIN scanned_videos_fts ON scanned_videos.rowid = scanned_videos_fts.rowid WHERE scanned_videos_fts MATCH :query || '*' LIMIT 50")
    fun search(query: String): Flow<List<ScannedVideo>>

    @Query("SELECT mediaStoreId, dateModified FROM scanned_videos")
    fun getAllTracked(): Flow<List<TrackedVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: ScannedVideo)

    @Query("DELETE FROM scanned_videos WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM scanned_videos WHERE mediaStoreId = :mediaStoreId")
    suspend fun getById(mediaStoreId: Long): ScannedVideo?

    @Query("SELECT * FROM scanned_videos WHERE extractedText != '' ORDER BY scannedAt DESC LIMIT 1000")
    fun getAllWithText(): Flow<List<ScannedVideo>>

    @Query("SELECT COUNT(*) FROM scanned_videos")
    fun getTotalCount(): Flow<Int>
}
